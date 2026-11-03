# 금융 알림 기반 개인 지출 관리 시스템

> **금융 앱의 비정형 알림을 거래 데이터로 바꿔, 지출 내역을 자동으로 기록하는 Android 앱**

**개인 캡스톤 프로젝트 · 2026.09 ~ 2026.10 · MVP 개발 완료**

Kotlin, Jetpack Compose, Room을 활용한 **온디바이스 지출 관리 앱**입니다. 금융 앱의 알림을 수집해 거래로 정규화하고, 사용자가 거래를 확인·수정하거나 중복·환불 의심 관계를 검토할 수 있도록 구성했습니다.

**현재 상태:** 핵심 기능을 구현한 캡스톤 MVP입니다. **Google Play에 출시한 상용 앱은 아니며**, 모든 금융사 또는 모든 알림 형식을 지원하지 않습니다.

## 1. 프로젝트 배경

금융 알림은 앱마다 표현 형식이 다르고, 하나의 거래가 여러 알림으로 나타나거나 취소·환불 알림이 뒤따를 수 있습니다. 이런 데이터를 무조건 거래로 확정하면 지출이 중복되거나 잘못 계산될 수 있습니다.

이 프로젝트에서는 다음 세 가지에 집중했습니다.

- **정규화:** 지원하는 알림 형식에서 금액·거래 유형·상대방 등의 필드를 추출해 공통 거래 모델로 변환
- **정합성 검토:** 중복·내부이체·환불 가능성을 후보로 제시하고, 사용자가 확정 또는 거절
- **소비 규칙:** 금액 또는 상대방 조건의 규칙을 저장하고 거래에 적용해 화면에서 경고 표시

원본 알림과 거래 정보는 **Android 기기의 Room DB에서 처리**하며, 현재 MVP는 별도 서버 연결 없이 동작합니다.

## 2. 주요 기능

| 구분 | 구현 내용 |
| --- | --- |
| 금융 알림 수집 | `NotificationListenerService`로 **카카오뱅크·삼성 월렛**의 허용된 패키지 알림을 수집 |
| 알림 정규화 | 카카오뱅크의 확인된 입금·출금 형식, 삼성 월렛의 확인된 **결제 완료** 형식을 각각 Parser로 처리 |
| 실패·제외 처리 | 미인식 알림은 원본과 `PARSE_FAILED` 상태를 보존하며 거래를 만들지 않음. 삼성 월렛 **포인트 적립** 알림은 `IGNORED` 처리 |
| 거래 관리 | 월별 거래 조회, 출금·입금 필터, 수동 거래 추가, 기존 거래 수정·삭제 |
| 거래 정합성 | 중복·내부이체·환불 **후보 탐지**, 확정/거절 및 재검토 상태 보존 |
| 월간 집계 | 총 출금·입금·순지출 표시, **사용자가 확정한** 중복·환불 관계를 집계에 반영 |
| 사용자 규칙 | 출금 금액 임계값·상대방 키워드 규칙의 등록·활성화·삭제 및 거래별 경고 표시 |
| 설정 | 알림 접근 권한 상태 확인과 시스템 설정 진입, 다크 모드, 데이터 초기화 |

### 앱 화면 구성

- **홈:** 월간 지출 요약, 주의가 필요한 거래, 최근 거래
- **거래:** 월별 목록·필터, 상세 내역, 수동 추가·수정·삭제, 정합성 후보 검토
- **규칙:** 금액·상대방 조건의 규칙 추가 및 관리
- **설정:** 금융 알림 수집 권한, 지원 형식 안내, 데이터 관리

> 실제 금융 알림과 거래 내역에는 개인정보가 포함될 수 있어, 공개 README에 실제 거래 화면이나 원문을 그대로 첨부하지 않았습니다. 화면 자료를 추가할 때는 가상 데이터 또는 완전히 마스킹한 캡처만 사용합니다.

## 3. 데이터 처리 구조

```text
카카오뱅크 / 삼성 월렛 알림
          │
          ▼
NotificationListenerService
          │ 허용 패키지 확인
          ▼
Room · raw_notifications        ← 원본 알림 우선 보존
          │
          ▼
Parser Registry
    ├─ KakaoBankNotificationParser
    └─ SamsungWalletNotificationParser
          │
          ├─ 인식 불가 → PARSE_FAILED (거래 생성 안 함)
          ├─ 적립 등 제외 → IGNORED (거래 생성 안 함)
          └─ 성공 → financial_transactions
                         │
                         ├─ 정합성 후보 탐지 및 저장
                         ├─ 활성 소비 규칙 평가
                         └─ Room Flow → Compose 화면
```

**핵심 처리 원칙**

1. **원본 우선 저장:** 파싱에 실패해도 허용된 금융 앱의 원본 알림을 남겨 원인을 확인할 수 있도록 구성했습니다.
2. **불확실한 거래는 강제 확정하지 않음:** 중복·내부이체·환불 판정은 후보로 관리하며 사용자의 검토 결과를 저장합니다.
3. **수정과 집계의 일관성:** 거래 수정 시 관련 후보·규칙 평가를 갱신하고, 확정된 중복·환불 관계만 월간 집계에 반영합니다.
4. **로컬 처리:** 금융 데이터 처리를 외부 서버가 아닌 앱 내부에서 수행합니다. 민감정보 보호를 위해 금융 알림 원문을 일반 로그에 남기지 않도록 처리했습니다.

## 4. 기술 스택

| 영역 | 기술 |
| --- | --- |
| 언어·플랫폼 | Kotlin, Android SDK |
| UI | Jetpack Compose, Material 3 |
| 데이터 저장 | Room / SQLite, Room Flow, 명시적 Migration (DB v1 → v6) |
| 비동기 처리 | Kotlin Coroutines |
| 핵심 Android API | `NotificationListenerService` |
| 테스트 | JUnit, Robolectric, Android instrumented / Compose UI test |
| 빌드·검증 | Gradle, GitHub Actions |

초기에 실험했던 `backend/`의 **Spring Boot API는 통신 PoC**입니다. 현재 Android MVP의 실행 경로에는 포함되지 않습니다.

## 5. 실행 및 테스트

**필요 환경:** Android Studio, JDK 21, 프로젝트가 지정한 Android SDK(compileSdk 37), Android 7.0(API 24) 이상 기기. Gradle Wrapper는 저장소에 포함되어 있습니다.

1. 이 저장소를 내려받고 Android Studio에서 **`android/` 폴더**를 엽니다.
2. Android SDK와 Gradle 동기화를 완료하고 `app`을 빌드·실행합니다.
3. 앱의 **설정 → 알림 접근 권한**에서 시스템 설정으로 이동해 권한을 허용합니다.
4. 지원되는 실제 금융 앱에서 해당 형식의 알림이 오면 거래 목록과 홈 화면에서 결과를 확인할 수 있습니다.

Windows PowerShell 기준:

```powershell
cd android

# 단위 테스트 및 두 가지 개발용 APK 빌드
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleExternalDebug

# Android 기기 또는 에뮬레이터 연결 후 화면 테스트
.\gradlew.bat :app:connectedDebugAndroidTest
```

- 일반 `debug` APK: `android/app/build/outputs/apk/debug/app-debug.apk`
- `externalDebug` APK: `android/app/build/outputs/apk/externalDebug/app-externalDebug.apk`
- `externalDebug`는 `.dev` 앱 ID를 사용해 일반 앱과 별도로 설치됩니다.
- **주의:** 기존 앱과 APK의 서명 키가 다르면 덮어쓰기 설치가 실패할 수 있습니다. 기존 거래를 보존해야 한다면 앱을 삭제하지 말고 서명/백업 상태를 먼저 확인하세요.

## 6. 검증 기록

| 범위 | 확인 근거 |
| --- | --- |
| 카카오뱅크 | 실제 Android 기기에서 **입금·출금 알림 → Room 거래 저장 → Compose 화면 표시** 확인 ([초기 구현 PR #3](https://github.com/0rchid241/finance-notification-expense-manager/pull/3)) |
| 거래 관리·정합성 UI | [PR #10](https://github.com/0rchid241/finance-notification-expense-manager/pull/10) 검증 기록: **단위 테스트 87개, 연결 기기 테스트 12개** 통과 |
| 삼성 월렛 | 실제 결제 알림 형식에 대한 파서 추가, 포인트 적립 제외 및 Room 연동 테스트 ([PR #14](https://github.com/0rchid241/finance-notification-expense-manager/pull/14)); 개발자가 실기기 동작 확인 후 병합 |
| 최신 `main` 빌드 | [PR #15](https://github.com/0rchid241/finance-notification-expense-manager/pull/15) 병합 후 GitHub Actions에서 **단위 테스트 및 일반/`externalDebug` APK 빌드 성공** ([실행 기록](https://github.com/0rchid241/finance-notification-expense-manager/actions)) |

테스트 수치는 **해당 PR 시점의 기록**이며, 모든 실제 금융사·결제·환불 상황에 대한 전수 검증을 의미하지 않습니다.

## 7. 지원 범위 및 한계

- 카카오뱅크는 **확인된 입금·출금 알림 형식**, 삼성 월렛은 **확인된 결제 완료 알림 형식**을 대상으로 합니다. 다른 형식·취소 알림을 모두 해석하는 범용 파서는 아닙니다.
- 같은 결제가 은행 앱과 삼성 월렛에서 각각 알림으로 오는 경우의 **출처 간 자동 중복 통합**은 지원하지 않습니다.
- 중복·내부이체·환불 관계는 자동 삭제·병합·확정하지 않습니다. 일부 집계 보정은 **사용자가 확정한 중복·환불**에만 적용됩니다.
- 규칙은 **금액·상대방 키워드** 조건을 지원합니다. 카테고리/기간/횟수 기반의 복합 규칙과 외부 푸시 알림은 포함되지 않습니다.
- 현재 MVP에는 금융 알림 원본의 사용자용 재처리 화면, 클라우드 동기화, 오픈뱅킹 연동, 정식 배포·운영이 포함되지 않습니다.

## 8. 저장소 안내

```text
.
├── android/    # Kotlin / Compose / Room 기반 MVP
├── backend/    # 초기 Spring Boot 통신 PoC (현재 미사용)
├── docs/       # 설계, 주간 진행 내역, 세부 구현 기록
└── README.md   # 프로젝트 개요 및 현재 MVP 상태
```

- [거래 수정·삭제, 후보 검토 및 월간 집계 구현 기록](docs/TRANSACTION_REVIEW.md)
- [수동 거래 추가 및 DB 마이그레이션 구현 기록](docs/MANUAL_TRANSACTIONS.md)
- [설계 문서](docs/architecture.md) · [요구사항](docs/requirements.md) · [초기 로드맵](docs/ROADMAP.md)

**문서 주의:** 일부 세부 문서와 초기 로드맵은 당시 개발 단계의 기록을 보존한 것으로, 과거 진행률(예: 47%)을 현재 완료율로 해석하면 안 됩니다. **현재 지원 기능과 완료 범위는 이 README 및 `main`의 코드를 기준으로 확인해 주세요.**

---

**프로젝트 기간:** 2026.09 ~ 2026.10 · **MVP 개발 완료**  
**프로젝트 유형:** 개인 캡스톤 / Android 개발 포트폴리오
