# Android 앱 — 개발 및 실행 가이드

> **2026.09~10 MVP 개발 완료 · 2026-11-03 코드 기준**  
> 이 디렉터리는 [금융 알림 기반 개인 지출 관리 시스템](../README.md)의 **실제 Android 실행 프로젝트**입니다. 초기 Spring Boot PoC는 이 앱의 필수 실행 요소가 아닙니다.

## 현재 기능

- **카카오뱅크:** 확인된 입금·출금 알림 수집·정규화
- **삼성 월렛:** 확인된 결제 완료 알림을 출금으로 정규화, 포인트 적립 알림은 거래 생성 없이 제외
- **거래 화면:** 월별·유형별 조회, 수동 거래 추가, 수정·삭제, 상세
- **정합성 검토:** 중복·내부이체·환불 후보 탐지 및 사용자 확정·거절·재검토
- **월간 요약:** 출금·입금·순지출, 확정 중복·환불의 제한된 집계 보정
- **사용자 규칙:** 지출 금액 기준, 상대방 키워드 기준 및 거래별 경고
- **앱 설정:** 알림 접근 권한 상태·설정 진입, 다크 모드, 데이터 초기화

지원 범위와 의도적으로 제외한 기능은 [최상위 README](../README.md)와 [MVP 요구사항](../docs/requirements.md)을 참고하세요.

## 개발 환경

- Android Studio와 프로젝트용 Android SDK
- JDK **21** (GitHub Actions에서 사용)
- Gradle Wrapper 포함 (시스템 Gradle 별도 설치 불필요)
- 빌드 설정: **compileSdk 37 / targetSdk 37 / minSdk 24** (`app/build.gradle.kts`)
- 실제 금융 알림 수집 검증에는 지원 앱이 설치된 Android 기기와 사용자의 알림 접근 권한 허용이 필요

### Windows PowerShell에서 빌드·검증

저장소 루트에서 실행합니다.

```powershell
cd android

# JVM 단위 테스트
.\gradlew.bat :app:testDebugUnitTest

# 일반 앱 및 별도 개발 앱 APK
.\gradlew.bat :app:assembleDebug :app:assembleExternalDebug

# Android 계측 테스트 APK만 빌드
.\gradlew.bat :app:assembleDebugAndroidTest

# 실제 Android 기기/에뮬레이터를 연결한 경우 화면 테스트 실행
.\gradlew.bat :app:connectedDebugAndroidTest

# 정적 검사
.\gradlew.bat :app:lintDebug
```

출력 위치:

- **일반 debug:** `app/build/outputs/apk/debug/app-debug.apk` — `com.orchid241.financenotificationmanager`
- **externalDebug:** `app/build/outputs/apk/externalDebug/app-externalDebug.apk` — `com.orchid241.financenotificationmanager.dev`

두 빌드는 서로 다른 앱 ID와 별도 앱 데이터/알림 권한을 가집니다. CI에서 만든 Debug APK는 기기에 설치된 기존 앱과 **서명 키가 다를 수 있으므로**, 기존 금융 데이터를 보존하려면 앱을 삭제하지 말고 서명과 데이터 백업 상태부터 확인해야 합니다.

[GitHub Actions](https://github.com/0rchid241/finance-notification-expense-manager/actions)는 단위 테스트와 두 APK 빌드를 실행해 아티팩트를 업로드합니다. 기기 연결 테스트는 개발자가 별도로 실행해야 하며, Firebase 자동 배포는 Secrets가 준비된 경우에만 조건부 동작합니다([안내](../docs/FIREBASE_APP_DISTRIBUTION.md)).

## 앱 사용 흐름

1. 앱 실행 후 **설정 → 알림 접근 권한**에서 Android 시스템 설정을 열어 수집 권한을 허용합니다.
2. 카카오뱅크 입출금 또는 삼성 월렛 결제 완료 알림이 발생하면 허용 패키지의 원본을 Room에 먼저 저장합니다.
3. 출처별 Parser가 인식한 경우 거래를 생성하고, 후보 관계와 활성 소비 규칙을 평가합니다.
4. **홈/거래**에서 지출·거래를 확인하고, 필요한 경우 거래를 수정하거나 후보를 확정·거절합니다.
5. **규칙** 탭에서 금액·상대방 규칙을 만들고 화면의 경고 표시를 확인할 수 있습니다.

**안전 주의:** 실제 결제를 만들거나 금융 알림 원문을 공개 자료에 붙여 넣을 필요는 없습니다. 시연에는 가상 데이터·마스킹된 화면을 사용하세요.

## 코드 찾아보기

| 구성 | 위치 | 설명 |
| --- | --- | --- |
| 수집 서비스 | `app/src/main/java/.../FinanceNotificationListenerService.kt` | Android 알림 수신, 허용 패키지 필터 |
| Parser | `.../parser/` | 카카오뱅크·삼성 월렛 형식별 정규화 |
| Repository | `.../data/FinancialTransactionRepository.kt` | 원본 저장·거래 생성·정합성·규칙 연계 |
| Room | `.../data/local/` | DB v6, DAO/Entity, Migration |
| 정합성 | `.../consistency/` | 중복·내부이체·환불 후보 조건 |
| 규칙 | `.../rules/` | 금액·상대방 조건 판정 |
| Compose UI | `.../ui/` | 홈/거래/규칙/설정, 수정·검토 화면 |
| 자동 테스트 | `app/src/test/`, `app/src/androidTest/` | JVM 및 계측/Compose 화면 테스트 |

`...`는 `app/src/main/java/com/orchid241/financenotificationmanager/`의 하위 경로를 줄여 적은 것입니다.

## 데이터 저장 원칙

- `raw_notifications`에 알림 원문을 보존하며 상태는 `PENDING` → `PROCESSED`, `PARSE_FAILED`, `IGNORED` 중 하나로 처리합니다.
- `financial_transactions`에는 정규화된 거래 또는 수동 입력 거래를 저장합니다. 수동 거래의 원본 알림 참조는 `null`입니다.
- `consistency_candidates`, `user_rules`, `rule_matches`를 별도로 저장합니다.
- Room 스키마 v1~v6을 보존하고 `MIGRATION_1_2`부터 `MIGRATION_5_6`까지 명시적으로 등록했습니다.
- 거래를 수정/삭제해도 원본 Raw는 변경하지 않습니다. 불확실한 후보 관계는 자동 확정하지 않습니다.

상세 설계는 [architecture.md](../docs/architecture.md), DB Migration과 수동 입력 검증은 [MANUAL_TRANSACTIONS.md](../docs/MANUAL_TRANSACTIONS.md), 후보 검토·집계 기준은 [TRANSACTION_REVIEW.md](../docs/TRANSACTION_REVIEW.md)를 참고하세요.

## 검증 이력과 한계

- [PR #10](https://github.com/0rchid241/finance-notification-expense-manager/pull/10) 시점에 **단위 테스트 87개, Android 연결 기기 테스트 12개** 통과 기록이 있습니다. 이는 해당 PR의 결과이며 지금 모든 시나리오를 증명하는 수치는 아닙니다.
- 카카오뱅크 실제 입금/출금 알림은 실기기에서 원본 저장 → 정규화 → Compose 표시를 확인했습니다.
- [PR #14](https://github.com/0rchid241/finance-notification-expense-manager/pull/14)에서 삼성 월렛의 실제 관찰 형식 기반 Parser를 병합했습니다. 다른 결제·취소 알림 형식의 전체 지원은 아닙니다.
- 지원 앱 외 알림은 수집하지 않으며, 허용 앱의 미인식 형식은 `PARSE_FAILED`로 보관합니다. 실패 Raw의 사용자용 재처리 화면은 아직 없습니다.
- 여러 금융 앱에서 같은 결제가 알림으로 오는 경우 자동 통합하지 않습니다. 완전한 실패 재시도/전달 보장, 스토어 출시·운영은 범위에 포함되지 않습니다.

**과거 문서 기록:** 초기 2026-10-01의 DB v2·테스트 25개/4개 등의 기록은 Git 커밋 이력에 보존되어 있습니다. 현재 구현 설명은 위 내용과 [최상위 README](../README.md)를 기준으로 확인하세요.
