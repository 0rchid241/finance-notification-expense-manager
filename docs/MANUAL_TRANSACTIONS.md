# 수동 거래 추가 및 시스템 뒤로가기 구현 보고서

검증일: 2026-10-12. 작업 브랜치: `feature/9-ui-foundation`.
작업 시작 전에 `git fetch origin`을 실행했고 로컬/원격 모두 `2859e4c`로 동일했다.
작업 전 존재하던 미추적 스키마 `3.json`, `4.json`은 보존했다.
커밋·push·PR 생성·merge는 수행하지 않았다.

## 수동 거래 입력과 화면

- 거래 목록 상단의 `+ 거래 추가`로 별도 입력 화면을 연다.
- 날짜와 시간은 기기 시간대의 Android 날짜/시간 선택기를 사용한다. 기본값은 현재 시각이다.
- 출금/입금, 금액, 상대방은 필수다. 금액은 양의 Long 정수이며 올바른 천 단위 쉼표 입력도 허용한다.
- 계좌 끝자리는 비어 있거나 ASCII 숫자 4자리여야 한다. 앞자리 0을 유지한다.
- 잔액은 비어 있거나 0 이상의 Long 정수여야 한다. 미입력과 0원은 구별한다.
- 소수·음수·잘못된 쉼표·Long 범위 초과는 임의 보정하지 않고 입력 안내를 표시한다.
- 저장 중 버튼을 비활성화하고, 저장 실패 시 입력값을 유지하며 재시도 안내를 표시한다.
- 기존 테마·간격·12dp 입력창/버튼을 사용하며 새로운 카드나 디자인 라이브러리를 추가하지 않았다.
- 저장 성공 후 해당 날짜의 월 목록과 전체 필터로 이동한다. 추가 화면에서 취소하면 기존 목록의 월/필터 상태를 유지한다.
- 상세 화면에서 미입력 계좌·잔액은 `정보 없음`으로 표시한다. 출처는 `수동 입력`이다.

## DB 변경과 기존 데이터 보존

DB 버전은 4에서 5로 올렸다. 파일명은 기존 `finance_notification_manager.db`를 유지한다.

| 필드 | 변경 | 의미 |
| --- | --- | --- |
| `rawNotificationId` | `Long` → `Long?` | 알림 거래는 기존 Raw ID 유지, 수동 거래는 null |
| `balance` | `Long` → `Long?` | 미입력 잔액은 null, 실제 0원은 0 |
| `accountLast4` | 기존 String 유지 | 선택 입력 미지정은 빈 문자열; 기존 엔진 계약 유지 |
| `source` | 기존 String 유지 | 수동 거래에 `수동 입력` 저장 |

새로운 출처 구분 테이블이나 가짜 RawNotification은 만들지 않았다.
Raw 외래 키와 참조 인덱스는 유지한다. null Raw 참조는 SQLite 외래 키 위반이 아니다.
기존 알림 거래의 값·ID·참조는 그대로 복사한다.

`MIGRATION_4_5`는 Room의 Migration 트랜잭션 안에서 다음 작업을 수행한다.

1. 정합성 후보·규칙 매치와 자동 생성 ID 이력을 임시 테이블에 복사한다.
2. 새 nullable 정의의 거래 테이블로 모든 거래를 복사한다.
3. 연결 테이블을 먼저 교체해 부모 거래 테이블 교체 시 CASCADE 데이터 유실을 방지한다.
4. 거래 테이블 이름과 외래 키·인덱스·연결 테이블을 복원하고 연결 데이터를 원래 ID로 복사한다.
5. 이전 자동 생성 ID보다 작은 ID를 재사용하지 않도록 이력을 복원한다.

원본 알림과 사용자 규칙 테이블은 그대로 둔다. 테이블 정의 변경을 위한 복사/재생성은 수행하지만,
사용자 데이터 삭제나 destructive migration fallback은 사용하지 않는다. 실패하면 Migration 트랜잭션이 롤백된다.
기존 1→2→3→4→5 경로도 등록되어 있다.

## 정합성 및 규칙 처리

`addManualTransaction`은 입력 검증 후 같은 Room 트랜잭션 안에서 거래를 삽입하고,
`TransactionConsistencyEngine.findCandidates`와 `TransactionRuleEngine.evaluate`를 호출한다.
알림 거래도 동일한 `saveTransactionAndEvaluate` 공통 경로를 사용한다.
엔진의 기존 판별 정책은 변경하지 않았다. 엔진 또는 결과 저장 실패 시 거래와 관련 결과를 함께 롤백한다.
Raw 생성이나 처리 상태 변경은 수동 거래에서 하지 않는다.
기존 Flow를 통해 목록·홈 요약·상태 Chip·규칙 경고에 반영된다.

## 시스템 뒤로가기

MainActivity의 Compose `BackHandler`로 다음 순서를 적용한다.

| 현재 화면 | 시스템 뒤로가기 결과 |
| --- | --- |
| 거래 상세 | 거래 목록 |
| 거래 추가 | 거래 목록 |
| 거래/규칙/설정 탭 | 홈 |
| 홈 | 콜백 비활성화, Android 기본 종료/백그라운드 전환 동작 |

홈에서 진입한 거래 상세도 목록으로 먼저 돌아간다. 탭 선택 시 상세/추가 화면 상태는 해제한다.
키보드나 시스템 날짜/시간 선택기가 열려 있으면 Android가 먼저 해당 UI의 뒤로가기를 처리할 수 있다.

## 로컬 검증 결과

다음 명령을 Android 디렉터리에서 실행해 성공했다.

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleExternalDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest :app:lintDebug
```

- 단위 테스트 총 67개 성공, 실패/오류/건너뛰기 0개.
- 추가된 14개: 입력 검증 4개, 수동 저장/엔진/롤백 7개, 실제 이전 스키마 Migration 3개.
- Migration 테스트는 버전 2·3·4에 원본과 거래를 넣고, 해당 버전의 정합성 후보·규칙 결과를 넣어 보존을 확인한다.
- null 저장, 앞자리 0, 잔액 0, 외래 키 검사, 삭제된 마지막 ID 이후의 자동 생성 ID, DB 재열기를 확인했다.
- 기존 Parser·알림 저장·정합성·규칙 테스트와 버전 1부터의 Migration 테스트도 통과했다.
- debug 및 externalDebug APK, Android 테스트 APK 빌드 성공.
- lint 오류 0개, 경고 23개: 의존성 업데이트 안내 11, 미사용 리소스 7, Modifier 매개변수 순서 3, 중복 라벨 1, KTX 사용 제안 1.
- `git diff --check` 통과.
- 새 Android 테스트 5개(입력 화면 2, 시스템 뒤로가기 3)를 작성해 컴파일했다. 연결 기기가 없어 실행하지 않았다.
- 실제 데이터가 있는 기기 업그레이드와 화면/시스템 뒤로가기 동작은 실기기 미검증이다.

## 변경 파일

아래 Kotlin 경로의 공통 기본 패키지는 `com/orchid241/financenotificationmanager`다.

| 구분 | 파일 |
| --- | --- |
| 수정 | `android/app/src/main/java/…/MainActivity.kt` |
| 수정 | `android/app/src/main/java/…/data/FinancialTransactionRepository.kt` |
| 수정 | `android/app/src/main/java/…/data/local/AppDatabase.kt` |
| 수정 | `android/app/src/main/java/…/data/local/FinancialTransactionEntity.kt` |
| 수정 | `android/app/src/main/java/…/ui/TransactionsScreen.kt` |
| 수정 | `android/app/src/main/java/…/ui/TransactionListScreen.kt` |
| 수정 | `android/app/src/test/java/…/data/local/AppDatabaseMigrationTest.kt` |
| 수정 | `android/README.md` |
| 추가 | `android/app/src/main/java/…/data/ManualTransactionInput.kt` |
| 추가 | `android/app/src/main/java/…/ui/AddTransactionScreen.kt` |
| 추가 | `android/app/src/test/java/…/data/ManualTransactionInputTest.kt` |
| 추가 | `android/app/src/test/java/…/data/local/ManualTransactionTest.kt` |
| 추가 | `android/app/src/test/java/…/data/local/ManualTransactionMigrationTest.kt` |
| 추가 | `android/app/src/androidTest/java/…/AddTransactionScreenTest.kt` |
| 추가 | `android/app/src/androidTest/java/…/SystemBackTest.kt` |
| 추가 | `android/app/schemas/com.orchid241.financenotificationmanager.data.local.AppDatabase/5.json` |
| 추가 | `docs/MANUAL_TRANSACTIONS.md` |

## 실기기 확인 항목

1. 기존 앱을 삭제하거나 데이터를 초기화하지 않고 동일한 applicationId의 APK로 업데이트한다.
2. 기존 원본·거래·정합성 후보·규칙 경고가 유지되는지 확인한다. externalDebug는 별도 앱이므로 debug DB 업그레이드 확인에 사용하지 않는다.
3. 수동 출금/입금을 각각 추가하고 선택 계좌/잔액 없이도 저장되는지 확인한다.
4. 계좌 `0001`과 잔액 0원을 입력해 미입력과 구분되는지 확인한다.
5. 날짜/시간 변경, 과거 월 입력, 저장 후 해당 월 표시, 홈 월간 요약 및 앱 재실행 후 유지를 확인한다.
6. 활성 금액/상대방 규칙에 맞는 수동 거래의 경고를 확인한다. 같은 수동 거래 또는 다른 계좌의 출금/입금으로 정합성 후보도 확인한다.
7. DB Inspector에서 수동 거래의 Raw ID가 null이고 가짜 원본 행이 생기지 않는지 확인한다.
8. 0원/음수/소수/잘못된 계좌 입력이 거부되는지 확인한다. 저장 연타·화면 회전·키보드 열림 상태도 확인한다.
9. 거래 상세와 추가 화면에서 시스템 뒤로가기를 누르면 목록으로 이동하는지 확인한다.
10. 거래/규칙/설정 탭에서 뒤로가기는 홈으로, 홈에서는 Android 기본 동작으로 이어지는지 확인한다.
11. 다크 모드에서도 입력창·오류 안내·목록이 기존 디자인과 어울리는지 확인한다.

기기 연결 후 자동 화면 테스트는 `:app:connectedDebugAndroidTest`로 실행할 수 있다.
미입력 계좌에 대해서는 기존 엔진이 내부이체/환불 후보를 만들지 않으며, 수동 입력만으로 알림의 중복 여부를 확정하지 않는다.
수정/삭제, 미저장 입력 확인 대화상자, 새로운 정합성 정책은 이번 범위에 포함하지 않았다.

향후 커밋 시 사용할 한글 메시지 예시: `feat: 수동 거래 추가 및 시스템 뒤로가기 지원`.
