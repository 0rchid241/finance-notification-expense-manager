# 시스템 구조 — 2026년 10월 MVP

> **현행 설명 (2026-11-03 기준)** · Kotlin/Jetpack Compose/Room 기반 Android 앱.  
> 현재 서비스는 별도 백엔드 호출 없이 **기기 내부에서 금융 알림을 수집하고 거래를 관리**합니다.

## 1. 데이터 처리 흐름

```text
카카오뱅크 / 삼성 월렛 (지원 패키지)
              │ Android 알림
              ▼
FinanceNotificationListenerService
              │ SupportedFinancialApps (허용 패키지)
              ▼
RawNotificationRepository ───→ raw_notifications (Room)
              │                       │
              │                       └─ PENDING 원본 우선 보존
              ▼
FinancialNotificationParserRegistry
       ├─ KakaoBankNotificationParser
       └─ SamsungWalletNotificationParser
              │
       ┌──────┼────────────────┐
       │      │                │
    Success  Ignored         Failure
       │      │                │
       │    IGNORED       PARSE_FAILED
       │      └────────────────┘
       │        금융 거래 미생성
       ▼
financial_transactions (Room)
       │
       ├── TransactionConsistencyEngine → consistency_candidates
       ├── TransactionRuleEngine        → rule_matches
       └── 처리 상태 PROCESSED
                    │
                 Room Flow
                    ▼
             Compose 4탭 UI
          홈 / 거래 / 규칙 / 설정
```

- `FinanceNotificationListenerService`는 수집을 `Dispatchers.IO`에서 수행합니다. 허용 패키지 외 알림은 처리하지 않습니다.
- 원본 Raw 알림은 **파싱 전에 별도로 저장**합니다. 파싱 성공 시 거래·후보·규칙 매치·Raw 상태 변경은 Room 트랜잭션 안에서 처리합니다.
- 파싱 실패는 정상 거래로 강제 변환하지 않습니다. 알림 수집 직후 프로세스가 종료되는 등 아직 커밋되지 않은 이벤트에 대한 완전한 전달 보장은 없습니다.
- 수동 입력 거래는 Raw 참조가 `null`이며, 자동 수집 거래와 **같은 정합성·규칙 평가 경로**를 사용합니다.

## 2. 금융 앱별 Parser

| 출처 | 지원하는 형식 | 파싱 정책 |
| --- | --- | --- |
| 카카오뱅크 (`com.kakaobank.channel`) | 확인된 입금·출금 알림 | 유형·금액·상대방·계좌 끝 4자리·잔액 등을 추출 |
| 삼성 월렛 (`com.samsung.android.spay`) | 확인된 **결제 완료** 알림 | 출금 거래로 정규화. 알림에 없는 계좌·잔액은 임의 생성하지 않음 |
| 삼성 월렛 포인트 적립 | 확인된 적립 알림 | 현금 입금이 아니므로 `IGNORED` (거래 미생성) |
| 그 외 형식 | 지원하지 않거나 인식 불가 | 허용 패키지의 인식 불가 알림은 `PARSE_FAILED`; 허용 외 패키지는 미수집 |

`FinancialNotificationParser` 인터페이스와 Registry를 통해 출처별 파싱 코드를 분리했습니다. 다만 **패키지 허용 목록과 Parser 모두 추가해야** 새 금융 앱을 실제로 지원할 수 있으며, 자동으로 모든 금융사를 인식하는 구조는 아닙니다.

## 3. Room 데이터 모델

현재 DB 버전은 **6**이며 `app/schemas/`에 1~6 스키마가 포함되어 있습니다. 명시적 Migration으로 기존 데이터를 보존하도록 구현했습니다.

| 테이블 | 책임 |
| --- | --- |
| `raw_notifications` | 알림 키·패키지·제목·본문·시각·처리 상태 보존 |
| `financial_transactions` | 정규화 또는 수동 입력된 거래의 금액·유형·상대방·출처·시각 등 |
| `consistency_candidates` | 거래 쌍의 중복·내부이체·환불 후보, 확정/거절/재검토 상태 |
| `user_rules` | 사용자가 저장한 금액 또는 상대방 조건과 활성 상태 |
| `rule_matches` | 규칙과 거래의 매치 결과·경고 메시지 |

Raw 상태: `PENDING`(저장 후 미완료), `PROCESSED`(거래 생성), `PARSE_FAILED`(해석 실패), `IGNORED`(의도적 제외).

거래를 수정·삭제해도 원본 알림은 별도로 보존합니다. 연결된 후보·규칙 결과는 거래 변경에 따라 무효화·재계산하거나 정리합니다.

## 4. 정합성 검토와 월간 집계

`TransactionConsistencyEngine`은 거래를 비교하여 다음 **후보**를 찾습니다.

- **중복 후보:** 같은 유형·금액·출처·계좌·상대방, 2분 이내
- **내부이체 후보:** 출금·입금 쌍, 같은 금액·서로 다른 계좌, 5분 이내
- **환불 후보:** 동일 금액·계좌·상대방, 출금 뒤 입금, 14일 이내

후보는 거래를 자동 삭제·합치거나 환불을 확정하지 않습니다. 사용자가 **확정 / 후보 아님**을 선택하며, 거래 편집 후 조건이 달라지면 재확인 상태가 될 수 있습니다.

`MonthlyTotals`는 확정된 **중복 출금**과 **환불 입금**을 순지출에 반영합니다. 미확정 후보는 지출 계산을 변경하지 않으며, 내부이체 확정은 현재 집계 보정 대상이 아닙니다. 계산 기준은 [거래 검토 문서](TRANSACTION_REVIEW.md)를 참고하세요.

## 5. 사용자 규칙

`TransactionRuleEngine`과 Room 저장소를 통해 다음 두 종류를 지원합니다.

1. **출금 금액 기준:** 한 건의 지출이 지정 금액 이상인 경우
2. **상대방 키워드:** 거래 상대방 이름에 특정 문자열이 포함된 경우

거래 저장 또는 수정 후 활성 규칙을 평가하여 `rule_matches`에 보관하고 Compose 화면에 경고를 표시합니다. 규칙 생성·활성화/비활성화·삭제가 가능합니다.

**현재 미지원:** 기간별 합계, 카테고리, 거래 횟수, 외부 푸시/시스템 경고 알림.

## 6. 화면과 권한

- **홈:** 월간 지출 요약·주의 거래·최근 거래
- **거래:** 월 선택, 필터, 상세, 추가·수정·삭제, 후보 검토
- **규칙:** 금액·상대방 규칙 관리
- **설정:** 알림 접근 권한 상태 확인 및 Android 설정 이동, 다크 모드, 데이터 관리

알림 접근 권한은 사용자가 Android 시스템 설정에서 직접 허용해야 합니다.

## 7. 테스트와 배포 경계

- JUnit/Robolectric 테스트는 Parser·Repository·DB Migration·정합성·규칙·집계 로직을 검증합니다.
- Android 계측/Compose 테스트는 테스트용 데이터를 사용해 화면 동작을 확인합니다. **실제 금융 앱 알림의 실기기 확인과는 별개**입니다.
- GitHub Actions는 단위 테스트와 `debug`·`externalDebug` APK 빌드를 수행합니다. 스토어 출시용 서명·배포는 완료하지 않았습니다.
- 원본 알림은 기기 내 Room에 저장하고 일반 로그에 민감한 본문을 출력하지 않도록 처리했습니다. **별도의 DB 암호화나 완전한 보안 감사를 완료했다는 뜻은 아닙니다.**

## 8. 범위 밖 구성 및 한계

`backend/`의 Spring Boot는 초기 전송 API 실험(PoC)이며 이 MVP에 연결되지 않습니다. 실패 Raw의 사용자용 조회·재처리, 모든 금융 알림 형식 지원, 출처 간 자동 중복 통합, 완전한 자동 환불 처리, 클라우드 동기화는 미구현 범위입니다.

[현재 로드맵](ROADMAP.md) · [MVP 요구사항](requirements.md) · [프로젝트 README](../README.md)
