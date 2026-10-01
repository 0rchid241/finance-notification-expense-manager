# Android Client

현재 MVP는 서버 없이 카카오뱅크 입출금 알림 수집부터 Compose 거래 목록까지 앱 내부에서 처리한다.

## 데이터 흐름

`SupportedFinancialApps` → `FinanceNotificationListenerService` → `FinancialTransactionRepository`
→ 기존 `RawNotificationRepository`로 Raw 저장 → `KakaoBankNotificationParser`
→ 거래 및 처리 상태를 Room 트랜잭션으로 저장 → DAO Flow → Compose `LazyColumn`.

- 지원 패키지는 `SupportedFinancialApps`에서 관리하며 현재 `com.kakaobank.channel`만 포함한다.
- 서비스는 지원하지 않는 신규 알림을 원본 추출 전에 제외한다. Repository도 같은 정책을 적용한다.
- 기존 다른 앱의 Raw는 삭제하지 않는다. Raw Repository 자체는 기존처럼 범용 저장 계층이다.
- null 제목/본문도 Raw에 보존한다. 각 콜백은 별도 행이며 동일 키의 거래도 병합하지 않는다.
- 서비스는 기존 `SupervisorJob + Dispatchers.IO`를 사용하고 종료 시 취소한다.
- 로그는 패키지, 저장/파싱 성공 여부만 남긴다. 원문과 예외 상세는 출력하지 않는다.

## Parser 규칙

Android API를 사용하지 않는 순수 Kotlin Parser다. 결과는 성공 데이터 또는 실패다.

- 제목 전체가 `출금 숫자원` 또는 `입금 숫자원` 형태여야 한다.
- 숫자는 쉼표 없는 정수 또는 올바른 천 단위 쉼표 형식이다. 음수, 소수, 잘못된 쉼표, Long 범위 초과는 실패한다. 거래 금액은 양수, 잔액은 0 이상이다.
- 본문은 경로와 잔액의 두 줄이며 줄바꿈은 CRLF/LF/CR 모두 지원한다. 앞뒤 공백은 제거한다.
- 출금은 화살표 왼쪽의 `입출금통장(4자리)`가 계좌, 오른쪽이 상대방이다. 입금은 반대다.
- 계좌는 문자열로 저장해 `0001` 등 앞자리 0을 보존한다. 상대방은 비어 있으면 실패한다.
- 잔액 줄 전체가 `잔액 숫자원`이어야 한다.
- 출처는 카카오뱅크다. 별도 거래 시각이 없으므로 `occurredAt`은 Raw의 `postedAt`을 사용한다.
- 미지원 패키지, null, 누락, 예상 밖 형식은 정상 거래로 강제 변환하지 않는다.

## Room 버전 2

DB 파일은 기존 `finance_notification_manager.db`를 유지한다. 버전별 스키마는 `app/schemas`에 보관한다.
명시적 `MIGRATION_1_2`는 `financial_transactions`와 Raw 참조 인덱스만 추가한다.
기존 `raw_notifications`는 변경하거나 삭제하지 않으며 destructive migration은 사용하지 않는다.

거래 필드: 자동 생성 Long ID, Raw ID, 거래 유형, Long 금액, 상대방, 문자열 계좌 끝 4자리,
Long 잔액, 출처, 발생 시각, 생성 시각. 시각은 epoch milliseconds다.
Raw ID는 외래 키로 연결하며 삭제 연쇄 처리는 하지 않는다. 중복 제거용 unique 제약은 없다.
거래 DAO는 insert와 발생 시각 내림차순 Flow를 제공하며 같은 시각은 ID 내림차순이다.

Raw는 먼저 별도 커밋되며 초기 상태는 `PENDING`이다.
성공 시 거래 삽입과 `PROCESSED` 변경을 하나의 트랜잭션으로 처리한다.
파싱 실패 시 거래를 만들지 않고 Raw만 `PARSE_FAILED`로 변경한다.
DB 후속 처리 실패 시 트랜잭션을 롤백하고 이미 저장된 Raw는 `PENDING`으로 보존한다.

## 화면

기존 Compose Theme와 Material 3를 사용한다. 최근 거래를 카드 목록으로 표시한다.
출금/입금은 한국어 라벨과 색으로 구분하고 입금 금액에는 +를 붙인다.
금액과 잔액은 천 단위 쉼표, 계좌는 `****1234`, 발생 시각은 기기 시간대로 `M월 d일 HH:mm`을 표시한다.
로딩/빈 목록/조회 오류 안내가 있다. Activity가 STARTED일 때 Flow를 관찰하므로 새 거래가 자동 반영된다.
추가 디자인 라이브러리는 없다. Preview와 화면 테스트에는 가짜 인물 및 계좌만 사용한다.

## 빌드 및 자동 테스트

기존 Gradle wrapper, SDK, Room 2.8.4, KSP 2.3.9, coroutines 1.9.0 설정을 유지한다.

```powershell
cd android
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest :app:lintDebug
# 연결된 Android 기기에서 화면 테스트
.\gradlew.bat :app:connectedDebugAndroidTest
```

- Parser: 정상 입출금, 줄바꿈, null, 숫자 및 본문 오류, 미지원 패키지, 0 잔액, 앞자리 0 계좌, 취소 제목 제외.
- 기존 Raw DAO: 필드/상태/null 보존, 중복 콜백 별도 저장, 범용 Raw Repository 계약.
- 거래 DAO/Repository: 필드와 최신 정렬, 원본 연결과 완료 상태, 실패 Raw 보존, 패키지 필터, 중복 별도 저장, 거래 삽입 및 상태 변경 실패의 롤백.
- Migration: 보관된 1.json으로 DB 생성 → 다른 앱/null Raw 저장 → 실제 Room Migration과 스키마 검증 → 새 거래 저장 → 닫고 다시 열어 영속성 확인.
- 기기 UI: 빈 안내, 출금 카드/쉼표/마스킹, 새 입금의 화면 반영.

## 2026-10-01 검증 결과

- 단위 테스트 **25개 성공**
  - Parser 11
  - 거래 DAO/Repository 7
  - Migration 1
  - 기존 Raw DAO 5
  - 기본 단위 테스트 1
- 연결 기기 테스트 **4개 성공**
  - Compose 3
  - 기존 앱 패키지 확인 1
- lint **오류 0개, 경고 19개**
- `git diff --check` 통과
- 실제 카카오뱅크 입금/출금 알림 E2E 검증 완료
  - 앱 화면을 열어두지 않은 상태에서 알림 수집
  - 구조화된 입금/출금 거래 2건이 `financial_transactions`에 저장되는 것 확인
  - 앱 최근 거래 카드에 입금/출금 2건 표시 확인
  - 상대방 이름과 계좌 끝 4자리만 구조화 데이터에 저장되는 것 확인

## 실제 카카오뱅크 알림 검증 절차

1. 기존 앱을 삭제하거나 데이터를 초기화하지 않고 새 APK로 업데이트한다.
2. 알림 접근 권한을 허용한다.
3. 앱 화면을 닫고 실제 카카오뱅크 출금 및 입금 알림을 발생시킨다.
4. 앱을 열어 최근 거래 카드의 출금/입금, 금액, 상대방, 계좌 끝자리, 잔액, 시각을 확인한다.
5. Database Inspector에서 새 Raw의 원문 보존, 거래의 Raw ID 참조, `PROCESSED` 상태를 확인한다.
6. 다른 앱 알림은 새 Raw가 추가되지 않는지 확인한다.
7. 예상 밖 카카오뱅크 알림은 Raw가 `PARSE_FAILED`로 남고 거래가 생성되지 않는지 확인한다.
8. 앱 재실행 후 원본과 거래가 유지되는지 확인한다.
9. Logcat에 제목/본문/상대방/계좌/예외 상세가 출력되지 않는지 확인한다.

## 한계와 다음 단계

- 현재 확인된 카카오뱅크 입금/출금 형식만 지원한다. 알림 형식이 바뀌면 파싱에 실패할 수 있다.
- 기존 Raw의 소급 처리와 `PENDING` 자동 재처리는 없다.
- 파싱 실패 Raw는 보존되지만 사용자가 앱에서 확인하는 화면은 아직 없다.
- 프로세스 또는 서비스 종료 이전에 커밋되지 않은 알림의 저장은 보장하지 않는다. 영속 큐/재시도는 이후 검토한다.
- 알림 접근 권한은 시스템 설정에서 수동으로 부여한다.
- 다음은 추가 금융 알림 형식 지원, 실패 데이터 확인/재처리 정책, 권한 UX를 진행한다.
- 중복/내부이체/취소/환불/카테고리/Rule Engine/통계/계정/동기화/서버 연결은 아직 구현하지 않았다.
