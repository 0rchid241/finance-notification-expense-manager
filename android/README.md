# Android Client

현재 MVP는 Android 앱 내부에서 처리한다. 이번 단계는 원본 알림의 Room 로컬 저장까지 구현한다.

## 데이터 흐름

`FinanceNotificationListenerService` → `RawNotificationRepository` → `RawNotificationDao` → Room

- 모든 패키지의 알림 콜백을 각각 보존하며 동일한 `notificationKey`도 중복 제거하지 않는다.
- 키, 패키지명, nullable 제목/본문, 게시 시각, 수신 시각을 저장한다. 시각은 epoch milliseconds다.
- `raw_notifications`의 기본 키는 자동 생성 Long이며 최초 처리 상태는 `PENDING`이다.
- `AppDatabase`는 applicationContext를 사용하는 singleton이고 버전은 1이다.
- DB 파일명은 `finance_notification_manager.db`이며 스키마는 `app/schemas`에 보관한다.
- DAO insert/전체 조회/상태별 조회는 suspend 함수다. 조회 순서는 ID 오름차순이며 콜백 발생 순서를 보장하지 않는다.
- 서비스는 `SupervisorJob + Dispatchers.IO`에서 저장하고 `onDestroy()`에서 취소한다.
- 저장 실패는 Logcat에 기록하며 다른 이벤트의 저장은 계속한다. 취소 예외는 다시 던진다.
- 서비스 종료나 프로세스 종료 이전에 커밋되지 않은 이벤트의 저장은 보장하지 않는다. 재시도/영속 큐는 이번 범위에 없다.

## 빌드 및 자동 테스트

기존 Gradle wrapper, AGP 내장 Kotlin, Compose 설정을 유지한다. version catalog로 Room 2.8.4, KSP 2.3.9, coroutines 1.9.0을 관리한다. Room 2.8의 coroutine API는 runtime에 포함된다.

```powershell
cd android
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest :app:lintDebug
```

`RawNotificationDaoTest`는 Robolectric 4.16.1 / SDK 28에서 실제 Room in-memory DB를 사용한다. 실기기 없이 필드 보존, 상태 조회, 동일 이벤트의 별도 행 저장, null 보존, Repository의 PENDING 저장을 검증한다. 앱의 compileSdk/targetSdk는 변경하지 않는다.

## 실기기 확인

1. 디버그 앱 설치 후 시스템 설정에서 알림 접근 권한을 부여한다.
2. 여러 앱의 알림을 발생시키고 기존 `FinanceNotification` Logcat 출력을 확인한다.
3. Android Studio Database Inspector에서 `finance_notification_manager.db`의 `raw_notifications`를 확인한다.
4. title/text, notificationKey, packageName, postedAt/receivedAt, `PENDING`을 확인한다.
5. 같은 키로 알림이 갱신될 때 ID가 다른 행이 추가되는지 확인한다.
6. 앱 프로세스 재시작 후 이미 커밋된 행이 유지되고 새로운 알림도 저장되는지 확인한다.

## 이후 확장

Parser/Normalizer는 Repository의 `getByProcessingStatus("PENDING")`를 진입점으로 사용할 수 있다. 처리 상태 갱신, 배치 조회, 정규화 결과 저장과 상태 변경의 트랜잭션, DB migration은 해당 단계에서 추가한다. 중복 판단은 정합성 계층에서 처리하며 원본 이벤트를 삭제하거나 덮어쓰지 않는다.

Parser, Normalizer, 거래 정합성, Rule Engine, UI 및 backend 통신은 아직 구현하지 않는다.
