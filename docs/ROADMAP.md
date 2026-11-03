# 개발 로드맵 — 2026년 10월 MVP 결과

> **현재 상태: MVP 개발 완료 (2026.09~2026.10)**  
> **문서 정리 기준: 2026-11-03 / 기본 브랜치 `main`**  
> Google Play 출시·상용 운영·모든 금융사 지원이 완료되었다는 뜻은 아닙니다.

## 목표와 범위 변경

초기에는 2026-10-31 Release Candidate를 목표로 자동 중복 제거, 취소·환불 자동 매칭, 카테고리/기간별 규칙 등 넓은 범위를 계획했습니다. 개발·발표 과정에서 **확인 가능한 금융 알림 처리와 사용자가 통제할 수 있는 거래 검토**에 집중하도록 10월 MVP 범위를 조정했습니다.

따라서 과거 로드맵에 기록된 **47%는 2026-10-10 당시의 초기 범위 기준 진행률**입니다. 현재 기능의 완료율로 사용하지 않으며, 범위가 달라진 뒤에는 임의의 새 백분율도 매기지 않습니다.

## MVP 구현 결과

| 영역 | 10월 MVP 결과 | 근거 |
| --- | --- | --- |
| Android 기반 | **구현 완료** — Kotlin/Compose, 알림 접근 설정, Room 로컬 처리 | `android/` |
| 금융 알림 수집 | **구현 완료** — 허용된 패키지의 원본 알림 우선 저장, 실패 상태 보존 | `FinanceNotificationListenerService`, `RawNotificationRepository` |
| 금융 알림 정규화 | **구현 완료(확인된 형식 한정)** — 카카오뱅크 입금/출금, 삼성 월렛 결제 완료·포인트 적립 제외 | [PR #3](https://github.com/0rchid241/finance-notification-expense-manager/pull/3), [PR #14](https://github.com/0rchid241/finance-notification-expense-manager/pull/14) |
| 거래 관리 | **구현 완료** — 월별 조회·필터, 수동 추가, 수정·삭제 | [PR #10](https://github.com/0rchid241/finance-notification-expense-manager/pull/10) |
| 거래 정합성 | **후보 검토형 MVP 구현** — 중복·내부이체·환불 후보 표시, 확정·거절·재검토 상태 저장 | `TransactionConsistencyEngine`, `docs/TRANSACTION_REVIEW.md` |
| 월간 집계 | **구현 완료(정의된 범위)** — 총 출금·입금, 순지출, 사용자 확정 중복·환불 반영 | `MonthlyTotals.kt` |
| 사용자 규칙 | **구현 완료(2종)** — 출금 금액 기준, 상대방 키워드 기준 / 활성화·삭제·화면 경고 | `TransactionRuleEngine`, `RulesScreen` |
| 기본 화면 | **구현 완료** — 홈·거래·규칙·설정 4탭, 다크 모드 | `ui/` |
| 개발용 빌드 | **구현 완료** — GitHub Actions 단위 테스트, `debug` / `externalDebug` APK 생성 | [PR #15](https://github.com/0rchid241/finance-notification-expense-manager/pull/15) |
| 정식 배포 | **범위 밖/미완료** — 스토어 출시 및 운영 검증은 하지 않음 | — |

## MVP 완료 기준

- [x] Android 실제 금융 알림을 허용 패키지 기준으로 수집하고 Room에 원본 저장
- [x] 카카오뱅크 입금·출금 및 삼성 월렛 확인된 결제 형식을 공통 거래로 변환
- [x] 알 수 없는 형식은 `PARSE_FAILED`, 결제와 무관한 포인트 적립은 `IGNORED`로 처리
- [x] 사용자가 거래를 보고 추가·수정·삭제할 수 있는 화면 제공
- [x] 중복·내부이체·환불 의심 거래의 후보 검토 흐름 제공
- [x] 금액·상대방 조건 규칙 저장 및 거래별 화면 경고
- [x] Room 스키마 v1→v6 마이그레이션과 관련 자동 테스트 코드 보유
- [x] 카카오뱅크 입출금 실기기 흐름 확인 및 삼성 월렛 실제 알림 형식 확인
- [x] `main` 기준 GitHub Actions 단위 테스트 및 개발용 APK 빌드 성공

**해석 주의:** 체크는 조정된 *MVP 범위*에 대한 것이며, 모든 실거래 상황을 대상으로 종단 간 검증했다는 뜻이 아닙니다. 예를 들어 자동화된 화면 테스트는 테스트용 데이터를 이용하며, 실제 금융 앱 알림 테스트와 구분됩니다.

## 의도적으로 남긴 한계 / 후속 후보

| 분류 | 미구현 또는 미확인 범위 |
| --- | --- |
| 지원 금융 앱 | 다른 금융사, 새로운 알림 템플릿, 삼성 월렛 취소 등 모든 형식 |
| 자동 정합성 | 서로 다른 출처 간 중복 통합, 원거래 자동 취소·병합, 완전한 멱등 재처리 |
| 거래 검토 | 부분 환불·복잡한 중복 그룹·자동 판정 확정 |
| 규칙 | 기간별·카테고리별·횟수별 누적 규칙, OS 푸시 경고 |
| 안정성 | 처리 실패 원본을 보는 사용자 화면과 자동 재처리, 기기·권한·알림 변형별 추가 검증 |
| 배포 | 정식 서명·배포/스토어 출시, 운영 모니터링, 클라우드 백업·동기화 |
| 포트폴리오 자료 | 개인정보를 제거한 실제 앱 화면 이미지 및 짧은 시연 영상 |

위 항목은 **다음 버전의 후보**이며 모두 구현하겠다는 약속이나 현재 MVP 완료를 부정하는 체크리스트는 아닙니다.

## 개발 과정의 주요 기록

- **9/24~10/01:** 알림 수집·Room 원본 보존·카카오뱅크 정규화와 실기기 확인
- **10/07~10/12:** Parser 공통 구조, 정합성 후보, 소비 규칙, 수동 거래 및 UI·DB v6까지 확장 ([PR #4~#10](https://github.com/0rchid241/finance-notification-expense-manager/pulls?q=is%3Apr+is%3Amerged))
- **10/30~10/31:** 삼성 월렛 확인된 결제 알림 파서 병합 및 일반/외부 테스트 APK 빌드 구성 ([PR #14](https://github.com/0rchid241/finance-notification-expense-manager/pull/14), [PR #15](https://github.com/0rchid241/finance-notification-expense-manager/pull/15))
- **11/03:** 대표 README 및 보조 문서에 최종 MVP 범위 반영

**검증 기록:** [PR #10](https://github.com/0rchid241/finance-notification-expense-manager/pull/10) 당시 단위 테스트 87개·연결 기기 테스트 12개가 통과했습니다. 이 숫자는 **해당 시점의 테스트 기록**으로, 최신 코드의 전수 검증 수치가 아닙니다. 최신 `main`의 단위 테스트와 APK 빌드는 [GitHub Actions](https://github.com/0rchid241/finance-notification-expense-manager/actions)에서 확인할 수 있습니다.

## 문서와 구현의 관계

- **현재 상태·지원 범위:** [프로젝트 README](../README.md)와 `main` 코드
- **현재 시스템 구조:** [architecture.md](architecture.md)
- **현재 MVP 요구사항:** [requirements.md](requirements.md)
- **당시 주간 계획(역사 기록):** [weekly-plan.md](weekly-plan.md)
- **세부 검증 기록:** [거래 검토](TRANSACTION_REVIEW.md), [수동 거래](MANUAL_TRANSACTIONS.md)

`backend/`는 초기 Spring Boot 통신 실험(PoC)으로 보존되어 있으며, 완성한 MVP의 필수 실행 구성요소가 아닙니다.
