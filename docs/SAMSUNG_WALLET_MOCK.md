# 삼성월렛 합성 알림 검증 — Issue #11

> 실제 삼성월렛 알림을 확보하지 못한 상태의 **모의 데이터 기준 구현**이다. 실제 삼성월렛 알림 수집·파싱 지원과 실기기 E2E 검증은 미완료다.
> 실제 `com.samsung.android.spay` 형식을 추측하지 않으며 운영 ParserRegistry와 지원 앱 표시는 카카오뱅크만 유지한다.

## 격리와 실행 방법

- `feature/11-samsung-wallet-mock-cache`에서 구현하며 PR 검토 전 병합하지 않는다.
- 합성 파서·캐시·처리기·fixture·Activity는 모두 `app/src/externalDebug`에만 존재한다. debug/release에는 빈 화면 진입점만 포함한다.
- `externalDebug` 앱 ID는 `com.orchid241.financenotificationmanager.dev`다. 실행 시에도 해당 ID/빌드 타입을 확인한다.
- 합성 도구는 **별도 `wallet_mock_scenarios.db`**만 연다. `.dev`의 실수집 가계부 DB인 `finance_notification_manager.db`와도 분리되어 실제 월 지출에 합산되지 않는다.
- non-exported Activity로 외부 앱이 임의 실행할 수 없다. 알림 접근 권한이나 실제 NotificationListener 주입 없이 설정의 `[TEST] 삼성월렛 합성 알림 검증 · 별도 DB`로 실행한다.
- 전체/개별 시나리오 실행 후 기대 상태·Raw/거래 증분을 비교한 성공 여부와 후보·규칙 결과 수를 확인한다. 아래 기존 거래 행 UI에 테스트 출처·금액·정합성/규칙 경고를 표시한다.
- 재실행은 새 UUID로 별도 실험을 추가한다. 기존 가계부/테스트 DB를 자동 초기화하지 않는다. 재실행 후에도 저장된 테스트 거래를 확인할 수 있다. 화면의 직전 실행 요약은 메모리 상태다.
- 테스트 출처와 상대방 및 Raw 제목/알림 key에 `[TEST]`를 표시한다. 패키지 이름은 가상 `dev.fixture.wallet`이다. 민감한 실측 알림과 금융 정보를 로그·문서·GitHub에 기록하지 않는다.

```powershell
cd android
.\gradlew.bat :app:assembleExternalDebug
adb install -r app/build/outputs/apk/externalDebug/app-externalDebug.apk
```

개발 앱 실행 → 설정 → 합성 알림 검증 → 전체 시나리오 실행. `검증 결과 성공 12 / 12`를 확인한다.
기존 일반 앱의 데이터 삭제나 앱 제거는 필요하지 않다.

## 합성 포맷과 처리 흐름

`externalDebug/assets/wallet_fixture_scenarios.json`은 앱 외부에서 읽을 수 있는 가상 시나리오 원본이다. 실제 삼성월렛 문구라는 주장은 하지 않는다.

- 제목: `[TEST] wallet-v1|합성이벤트ID|합성거래발생시각|OUT 또는 IN|payment`
- 본문: `amount=1000;merchant=가상상점` (조각별 선택 필드: `account`, `balance`)
- 금액은 양수 Long, 잔액은 0 이상 Long, 선택 계좌는 네 자리다. 빈 본문·빈 값·중복 키·미지 필드·홍보·형식 오류·범위 초과는 거래로 만들지 않는다.
- 금액과 상대방이 모두 있어야 저장한다. 잔액은 `null`, 계좌는 기존 모델의 빈 문자열로 미상 표현하며 임의 값을 채우지 않는다.

1. 수신 콜백마다 변경하지 않은 Raw 행을 먼저 저장한다.
2. `SyntheticWalletParser`가 합성 패키지/포맷을 검증하고 이벤트 식별자와 부분 필드를 읽는다. 기존 `FinancialNotificationParser` 계약을 구현한다.
3. Raw의 완료 처리 영수증으로 이미 저장된 이벤트인지 검사한다.
4. 메모리 캐시에서 같은 식별자의 상호 보완 필드만 합친다. 원본 Raw 본문을 수정하거나 가공 Raw를 추가하지 않는다.
5. 완성된 조각을 `ParsedFinancialTransaction`으로 변환한 뒤 기존 Repository의 `processStoredNotification`으로 보낸다.
6. 기존 `saveTransactionAndEvaluate` → `TransactionConsistencyEngine` → `TransactionRuleEngine` 경로로 거래/후보/규칙/완료 상태를 원자 저장한다.
7. 테스트 DB Flow가 화면을 갱신한다. 같은 금액의 별도 이벤트는 거래 두 건을 유지하고 기존 엔진이 필요시 중복 **후보**로만 표시한다.

완성 콜백의 Raw ID가 거래에 연결된다. 앞선 부분 Raw는 동일한 합성 이벤트 식별자를 통해 연결할 수 있다.
거래 저장 실패 시 Raw는 `PENDING`으로 남고 거래·후보·규칙·완료 영수증은 함께 롤백한다. 메모리 캐시를 비워 이후 재수신을 차단하지 않는다. 자동 재처리 작업은 이번 범위에 추가하지 않는다.

공통 변경은 `ParsedFinancialTransaction.balance: Long?`와 기존 Repository 저장 경로 추출, 설정의 variant별 슬롯뿐이다. 카카오뱅크는 계속 파서에서 필수 잔액을 검증한다. **Room DB는 6 유지**, Entity/스키마/Migration 변경과 destructive migration은 없다.

## 캐시 정책과 한계

| 항목 | 정책 |
| --- | --- |
| 식별 | 패키지 + 알림 key + 명시적 합성 이벤트 ID + 거래 발생 시각 + 출금/입금 유형 |
| TTL | 최초 조각 수신 기준 5초, 정확히 5초부터 만료. 중복 조각으로 연장하지 않음 |
| 상한 | 64개, 최초 삽입 순으로 가장 오래된 이벤트 퇴출 |
| 필드 충돌 | 같은 식별자에서 금액/상대방/계좌/잔액 충돌 시 해당 캐시 이벤트를 차단. TTL 만료 전 덮어쓰지 않음 |
| 시계 역행 | 기존 조각을 버리고 새로 대기. 과거/현재 조각을 잘못 병합하지 않음 |
| 완료 중복 | TTL·상한과 별도로 Raw의 `PROCESSED` 영수증으로 억제 |
| 재시작 | 미완료 조각은 복구/병합하지 않음. 단독 완전 알림만 새 저장 가능. 완료 영수증은 DB에서 복구 |
| 동시성 | 처리기 Mutex 및 동일 DB Room 트랜잭션으로 별도 처리기에서도 완료 검사·생성 직렬화 |

상태는 `MOCK_WAITING`, `MOCK_DUPLICATE`, `MOCK_INVALID`, `MOCK_CONFLICT`, `PROCESSED`이며 각 Raw 행에 저장한다.
완료 후 같은 식별자의 후속 본문은 원거래를 수정하지 않고 중복으로 보존한다. 확정된 완료 기록보다 캐시를 우선하지 않는다.
캐시 만료/퇴출로 부분 필드가 사라지면 모르는 값은 추측하지 않는다. 처리 영수증은 거래를 삭제해도 Raw에 남아 같은 이벤트가 되살아나지 않는다.
식별자에는 **합성 포맷만의 명시적 이벤트 ID/시각**이 있다. 실제 앱의 notification key가 결제 ID라는 가정으로 운영 적용할 수 없다.
별도 테스트 DB라 완료 확인은 Raw 조회로 구현했다. 장기간 실수집에 적용할 때는 실제 식별 근거와 조회 비용을 검토해야 한다. 현재 캐시는 기기 시계 기반 수신 시간을 사용하며 실제 서비스용 단조 시계/세션 설계는 실측 후 결정한다.

## 제공 시나리오

1. 단일 완전 알림 → 거래 1건
2. 금액 조각 → 상대방 조각 → 거래 1건
3. 동일 알림 key·동일 금액, 서로 다른 이벤트 ID → 거래 2건
4. 상대방 조각 → 금액 조각 (순서 역전) → 거래 1건
5. 동일 콜백 반복 → Raw 2건 / 거래 1건
6. 5초 TTL 경계 초과 → 불완전 조각 두 개 / 거래 0건
7. 빈 본문·홍보·잘못된 금액 → 거래 0건
8. 같은 이벤트 필드 충돌 → 거래 0건
9. 완료 후 처리기 재시작 → 거래 추가 없음
10. 부분 알림 후 처리기 재시작 → 이전 조각과 합치지 않음
11. 별도 이벤트의 상호 보완 조각 → 병합 금지
12. 완료 후 TTL 만료 → 영수증으로 거래 추가 없음

시나리오의 `delay`는 가상 수신 시각 오프셋이며 실제 대기나 시스템 금융 알림 발송은 하지 않는다. 재시작 시나리오는 처리기/캐시 객체 재생성이다. 실제 OS 프로세스 종료는 별도 파일 DB 재열기 테스트 및 수동 확인으로 구분한다.

## 자동 검증

- 지정 명령 `:app:assembleDebug :app:assembleExternalDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest :app:lintDebug` 성공.
- 추가 `:app:testExternalDebugUnitTest :app:lintExternalDebug :app:assembleRelease :app:assembleExternalDebugAndroidTest` 성공.
- debug 단위 테스트 87개, externalDebug 113개(기존 87 + 합성 전용 26) 모두 성공. 실패/오류/건너뛰기 0.
- 신규 26개: 파서 6, 순수 캐시 9, Repository/Room/JSON 통합 11. 파일 DB 재열기·캐시 퇴출/만료·동시 중복 콜백·서로 다른 처리기·저장 실패 롤백·카카오뱅크 회귀를 검증했다.
- debug와 externalDebug Lint 각각 오류 0 / 경고 23. 기존 의존성 안내 11, 미사용 리소스 7, ModifierParameter 3, RedundantLabel 1, UseKtx 1이며 이번 구현의 새 경고는 없다.
- 연결 Android 기기에 `.dev`와 테스트 APK만 `adb install -r`로 업데이트 설치했다. 기존 일반 앱/DB 초기화·제거는 실행하지 않았다.
- 기기에서 `WalletMockScenarioTest` 1개 성공. 실제 개발 화면 버튼을 통해 JSON 12개 시나리오를 실행하고 `검증 결과 성공 12 / 12`를 확인했다.
- APK DEX/asset 검사: debug/release에는 합성 Activity·파서·fixture가 없고 externalDebug에만 존재한다.
- `git diff --check` 통과. 실제 삼성월렛 알림 검증 결과로 해석하지 않는다.

```powershell
# android 디렉터리에서 실행
.\gradlew.bat :app:assembleDebug :app:assembleExternalDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest :app:lintDebug
.\gradlew.bat :app:testExternalDebugUnitTest :app:lintExternalDebug :app:assembleRelease :app:assembleExternalDebugAndroidTest
adb install -r app/build/outputs/apk/externalDebug/app-externalDebug.apk
adb install -r app/build/outputs/apk/androidTest/externalDebug/app-externalDebug-androidTest.apk
adb shell am instrument -w -r -e class com.orchid241.financenotificationmanager.mockwallet.WalletMockScenarioTest com.orchid241.financenotificationmanager.dev.test/androidx.test.runner.AndroidJUnitRunner
```

## 변경 파일 (23개)

기본 Kotlin 패키지는 `com/orchid241/financenotificationmanager`다. `…`는 해당 기본 패키지를 뜻한다.

| 구분 | 경로 |
| --- | --- |
| 빌드 | `android/app/build.gradle.kts` |
| 공통 UI | `android/app/src/main/java/…/MainActivity.kt`, `ui/SettingsScreen.kt` |
| 공통 저장 경로 | `android/app/src/main/java/…/data/FinancialTransactionRepository.kt` |
| 공통 파싱 결과 | `android/app/src/main/java/…/parser/KakaoBankNotificationParser.kt` |
| 일반 빌드 빈 진입점 | `android/app/src/debug/java/…/WalletMockEntry.kt`, `android/app/src/release/java/…/WalletMockEntry.kt` |
| 개발 진입점 | `android/app/src/externalDebug/AndroidManifest.xml`, `java/…/WalletMockEntry.kt` |
| 개발 파서 | `android/app/src/externalDebug/java/…/mockwallet/SyntheticWalletParser.kt` |
| 개발 캐시 | `android/app/src/externalDebug/java/…/mockwallet/WalletEventCache.kt` |
| 개발 저장 연결 | `android/app/src/externalDebug/java/…/mockwallet/SyntheticWalletProcessor.kt` |
| 개발 화면 | `android/app/src/externalDebug/java/…/mockwallet/WalletMockActivity.kt` |
| 개발 시나리오 | `android/app/src/externalDebug/java/…/mockwallet/WalletScenarios.kt`, `android/app/src/externalDebug/assets/wallet_fixture_scenarios.json` |
| 단위 테스트 | `android/app/src/testExternalDebug/java/…/mockwallet/SyntheticWalletParserTest.kt`, `WalletEventCacheTest.kt`, `SyntheticWalletProcessorTest.kt` |
| 기기 테스트 | `android/app/src/androidTestExternalDebug/java/…/mockwallet/WalletMockScenarioTest.kt` |
| 문서 | `README.md`, `android/README.md`, `docs/ROADMAP.md`, `docs/SAMSUNG_WALLET_MOCK.md` |

## 실기기 확인

1. `.dev`를 업데이트 설치한 뒤 일반 앱과 별도로 실행되는지 확인한다. 데이터 초기화 없이 기존 카카오뱅크 거래와 검토 결과가 남아 있어야 한다.
2. 개발 앱 설정에서 전체 시나리오를 실행하고 12/12 성공, Raw/거래·후보·규칙 수를 확인한다.
3. 같은 금액 별도 거래는 두 행을 유지하고 후보 경고만 생기는지 확인한다. 중복 콜백은 한 거래만 생성되어야 한다.
4. 불완전·만료·충돌·빈 본문에서는 거래가 생성되지 않는지 확인한다.
5. 개발 앱을 종료/재실행해 테스트 거래가 남고 가계부 홈/월 집계에는 포함되지 않는지 확인한다.
6. debug/release에는 해당 도구가 없고 지원 앱 설명은 카카오뱅크만 표시되는지 확인한다.

## 실제 삼성월렛 지원 전 남은 작업

- 사용자 동의하에 실제 결제·취소·업데이트 알림을 확보하고 패키지명, title/text/확장 필드, notification key 재사용 패턴·순서를 기기에서 확인한다.
- 실제 이벤트 동일성 증거를 정한 뒤 금액/상대방이 없거나 식별이 모호하면 자동 병합하지 않는 정책을 적용한다.
- 실측 fixture는 금융 개인정보를 비식별화하고 별도 실제 파서·테스트로 작성한다. 합성 파서를 운영 등록하지 않는다.
- 캐시 TTL/단조 시계/상한 및 영수증 조회·재처리 정책을 실측에 맞춰 보정하고 필요할 때만 안전한 Migration을 설계한다.
- 실제 NotificationListener → Raw → 실제 파서 → 저장/정합성/규칙/월 집계 E2E를 별도 검증한 뒤 운영 지원 표시를 결정한다.
- 이번 PR에서는 새로운 기능으로 범위를 확대하거나 실제 지원 완료로 로드맵 진행률을 올리지 않는다.
