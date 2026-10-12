# 거래 수정·삭제, 정합성 검토와 월 집계 구현 보고서

작업일: 2026-10-12. 브랜치: `feature/9-ui-foundation`.
시작 전에 `git fetch origin`을 완료하고 로컬과 원격이 `c8f2c6b`로 동일함을 확인했다.
기존 데이터 모델·Repository·정합성/규칙 엔진·화면·디자인 시스템을 읽은 뒤 수정했다.
커밋·push·PR 생성·merge는 수행하지 않았다. backend 수정도 없다.

## 거래 상세 수정과 삭제

수동/알림 거래 모두 날짜·시간, 출금/입금, 금액, 상대방, 계좌 끝자리, 잔액을 수정할 수 있다.
기존 입력 화면을 재사용해 저장 값으로 폼을 초기화한다.
거래 ID·원본 참조·출처·생성 시각을 유지하며 RawNotification은 수정하지 않는다.
기존 입력 검증을 그대로 적용한다.

삭제는 확인 다이얼로그를 거친다. 거래 삭제 시 연결 후보와 규칙 결과는 기존 외래 키의 CASCADE로 정리된다.
원본 금융 알림은 별도 테이블에 계속 보존한다. 취소하면 삭제하지 않는다.
실패 시 오류를 표시하고 재시도할 수 있다.

수정 화면의 상단/시스템 뒤로가기는 상세로, 상세의 뒤로가기는 거래 목록으로 이동한다.
거래/규칙/설정 탭은 홈으로, 홈에서는 Android 기본 동작을 유지한다.
목록의 월/필터 상태는 보존하며 수정 저장 후 해당 날짜의 월을 준비한다.

## DB 버전 6과 Migration

`consistency_candidates`에 다음 필드를 추가한다.

| 필드 | 저장 값과 의미 |
| --- | --- |
| `reviewStatus` | 미검토, 확정, 거절, 수정 후 재확인 상태 |
| `isActive` | 현재 거래 내용이 자동 탐지 조건을 만족하는지 여부 |
| `reviewedAt` | 마지막 사용자 검토 시각, 미검토는 null |

코드 식별자는 `PENDING / CONFIRMED / REJECTED / NEEDS_REVIEW`를 사용한다.
UI에서는 후보·확정된 중복·확정된 환불·재확인 필요 등의 한국어로 표시한다.

명시적 `MIGRATION_5_6`은 ALTER TABLE로 위 세 컬럼만 추가한다.
기존 후보는 미검토, 활성, 검토 시각 null로 시작한다. 이전 단계에는 사용자 확정 기능이 없었으므로
기존 후보를 임의로 확정하거나 집계 제외하지 않는다.
거래·원본·규칙·규칙 결과, 후보 ID·관계·사유·생성 시각 및 인덱스/외래 키는 보존한다.
기존 1→2→3→4→5→6 경로를 등록하며 destructive migration은 사용하지 않는다.
스키마는 `app/schemas/...AppDatabase/6.json`에 생성했다.

## 후보 확정과 거절

각 후보에 연결 거래의 상대방·유형·금액·시각과 확정/후보 아님 액션을 표시한다.
중복 확정 전후에 어떤 거래가 제외되는지도 안내한다.

- 중복 확정: 같은 쌍에서 ID가 큰 거래를 집계 제외 대상으로 계산한다. 두 거래 모두 물리적으로 보존한다.
- 환불 확정: 출금과 이후 입금의 관계를 확정한다. 입금 거래의 금액을 환불액으로 반영한다.
- 내부이체 확정: 관계 검토 상태만 확정한다. 이번 범위에서는 순지출을 조정하지 않으며 화면에도 안내한다.
- 후보 아님: 행을 삭제하지 않고 거절과 검토 시각을 저장한다. 해당 후보 경고와 상세 검토 영역을 숨긴다.
- 확정된 관계에서도 후보 아님으로 변경할 수 있다. 이 경우 집계 보정을 해제한다.

같은 거래에 확정 관계가 겹쳐 이중 차감되지 않도록 한 거래당 활성 확정 관계 하나만 허용한다.
겹치는 관계를 확정하려면 기존 확정을 후보 아님으로 바꿔야 하며 오류 안내로 설명한다.
같은 후보를 반복 확정해도 거래를 추가 삭제하거나 금액을 중복 차감하지 않는다.

## 재평가 전략

편집 전체는 Room 트랜잭션으로 실행한다.

1. 기존 거래 ID를 유지하고 입력 값을 갱신한다.
2. 금액·시간·유형·상대방·계좌가 바뀌면 연결된 기존 확정을 재확인 상태로 변경한다. 잔액만 바뀌면 확정을 유지한다.
3. 해당 거래의 연결 후보를 비활성화한 후 기존 정합성 엔진으로 현재 값을 재평가한다.
4. 거래쌍과 관계 유형이 같은 행은 재사용하고 사유/탐지 여부만 갱신한다. 양쪽 ID 순서가 바뀌어도 같은 쌍으로 찾는다.
5. 사용자 거절 상태와 검토 시각은 자동 탐지로 덮어쓰지 않는다. 사라졌다가 같은 관계가 다시 탐지돼도 거절은 유지한다.
6. 해당 거래의 규칙 결과를 지우고 현재 활성 규칙을 다시 평가한다. 비활성 규칙을 재활성화할 때도 현재 거래를 평가한다.

수정 후 조건이 맞지 않는 이전 확정은 재확인 상태로 남겨 상세에 설명하고, 다시 확정할 수 없으며 집계에도 반영하지 않는다.
조건을 여전히 만족하면 사용자가 재확정할 수 있다. 재확인/거절/미검토만으로 집계는 바꾸지 않는다.
자동 전체 재계산에서도 기존 사용자 검토 상태를 유지한다.
규칙 저장 등 후속 단계가 실패하면 거래 수정·후보 상태·규칙 결과를 함께 롤백한다.
거절은 거래쌍과 관계 유형 기준이다. 다른 유형의 새로운 관계까지 임의로 거절하지 않는다.

## 월 집계 기준

홈과 거래 화면은 같은 순수 Kotlin 집계 함수를 사용한다.
기존 요약 카드 안에서 순지출, 총 출금, 총 입금, 환불 확정, 중복 제외 금액을 표시한다.
거래 행은 기존 Row 구조, 상태는 기존 Chip을 유지한다. 후보 검토에 새 카드를 추가하지 않았다.

| 값 | 기준 |
| --- | --- |
| 총 출금 | 해당 월의 저장된 출금 전체 합계, 중복도 포함 |
| 총 입금 | 해당 월의 저장된 입금 전체 합계, 중복/환불도 포함 |
| 중복 제외 출금 | 확정 중복 쌍의 제외 거래가 출금이며 해당 월에 발생한 금액 |
| 중복 제외 입금 | 확정 중복 쌍의 제외 거래가 입금이며 해당 월에 발생한 금액 |
| 중복 제외 금액 | 위 출금/입금 제외 금액의 합계. UI에 방향별 내역도 표시 |
| 환불 확정 금액 | 확정 환불의 입금 거래가 해당 월에 발생한 금액 |
| 순지출 | 총 출금 − 중복 제외 출금 − 환불 확정 금액 |
| 반영 입금 | 총 입금 − 중복 제외 입금 |

미검토 후보·거절·재확인·비활성 관계는 보정하지 않는다. 일반 입금은 순지출에서 차감하지 않는다.
이전 월의 지출을 이번 달에 환불하면 이번 달의 환불액으로 반영한다. 순지출이 음수일 수도 있으며 0으로 잘라내지 않는다.
중복 거래쌍이 월 경계에 걸치면 제외 거래가 발생한 월에서만 차감한다.
부분 환불·여러 건의 중복 그룹 병합은 이번 범위에 포함하지 않는다.

## 검증

요청된 다음 명령을 실행해 성공했다.

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleExternalDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest :app:lintDebug
```

- 단위 테스트 87개 성공, 실패/오류/건너뛰기 0개.
- 추가 20개: 거래 편집/삭제/검토/롤백 12, 월 집계 6, 검토 영속성 1, 버전 5 Migration 1.
- 기존 67개 Parser·Raw·수동 입력·정합성·규칙·UI 상태·Migration 테스트도 통과했다.
- 실제 이전 스키마의 버전 1부터 및 버전 2·3·4·5 업그레이드 경로, 연결 데이터 보존, 외래 키 검사, 재열기를 검증했다.
- 검토 결과를 파일 DB에 저장한 뒤 닫고 다시 열어 확정/거절과 집계가 유지되는지 검증했다.
- debug/externalDebug/Android 테스트 APK 빌드 성공.
- lint 오류 0개, 경고 23개: 의존성 안내 11, 미사용 리소스 7, Modifier 매개변수 순서 3, 중복 라벨 1, KTX 제안 1.
- `git diff --check` 통과.
- 신규 Android 화면 테스트 3개를 작성했다: 삭제 확인/취소, 확정/거절 UI 변화, 편집 폼 초기화/저장.
- 기기에는 `adb install -r`로 앱/테스트 APK를 업데이트 설치했으며 데이터 초기화나 삭제 명령은 실행하지 않았다.
- 첫 기기 테스트는 잠금 상태로 화면을 찾지 못했다. 잠금 해제 후 테스트 Activity에만 화면 켜짐 유지를 적용했고, 탭 전환 뒤 뒤로가기 호출 전에 Compose 대기를 추가했다.
- 최종 연결 기기 Android 화면 테스트 12개 모두 성공(`OK (12 tests)`). 신규 상세 액션 3개, 기존 입력 2개·뒤로가기 3개·목록 3개·앱 컨텍스트 1개를 검증했다.
- 상세/수정 화면의 실제 앱 탐색과 실제 금융 데이터의 월 집계는 아래 수동 확인 항목으로 남긴다. 화면 테스트의 상세/편집 데이터는 메모리 fixture이며 실제 사용자 거래는 수정·삭제하지 않았다.

## 변경 파일 목록

Kotlin 경로의 기본 패키지는 `com/orchid241/financenotificationmanager`다.

| 구분 | 파일 |
| --- | --- |
| 수정 | `android/app/src/main/java/…/MainActivity.kt` |
| 수정 | `android/app/src/main/java/…/data/FinancialTransactionRepository.kt` |
| 수정 | `android/app/src/main/java/…/data/local/AppDatabase.kt` |
| 수정 | `android/app/src/main/java/…/data/local/ConsistencyCandidateEntity.kt` |
| 수정 | `android/app/src/main/java/…/data/local/ConsistencyCandidateDao.kt` |
| 수정 | `android/app/src/main/java/…/data/local/FinancialTransactionDao.kt` |
| 수정 | `android/app/src/main/java/…/data/local/RuleDaos.kt` |
| 수정 | `android/app/src/main/java/…/ui/AddTransactionScreen.kt` |
| 수정 | `android/app/src/main/java/…/ui/TransactionsScreen.kt` |
| 수정 | `android/app/src/main/java/…/ui/HomeScreen.kt` |
| 수정 | `android/app/src/main/java/…/ui/TransactionConsistencyUi.kt` |
| 수정 | `android/app/src/main/java/…/ui/TransactionRow.kt` |
| 수정 | `android/app/src/main/java/…/ui/StatusChip.kt` |
| 수정 | `android/app/src/test/java/…/data/local/AppDatabaseMigrationTest.kt` |
| 수정 | `android/app/src/test/java/…/data/local/ManualTransactionMigrationTest.kt` |
| 수정 | `android/app/src/androidTest/java/…/AddTransactionScreenTest.kt` |
| 수정 | `android/app/src/androidTest/java/…/SystemBackTest.kt` |
| 수정 | `android/app/src/androidTest/java/…/TransactionListScreenTest.kt` |
| 수정 | `android/README.md` |
| 추가 | `android/app/src/main/java/…/data/MonthlyTotals.kt` |
| 추가 | `android/app/src/main/java/…/ui/MonthlyTotalsContent.kt` |
| 추가 | `android/app/src/test/java/…/data/MonthlyTotalsTest.kt` |
| 추가 | `android/app/src/test/java/…/data/local/TransactionReviewTest.kt` |
| 추가 | `android/app/src/test/java/…/data/local/TransactionReviewPersistenceTest.kt` |
| 추가 | `android/app/src/androidTest/java/…/TransactionDetailActionsTest.kt` |
| 추가 | `android/app/schemas/com.orchid241.financenotificationmanager.data.local.AppDatabase/6.json` |
| 추가 | `docs/TRANSACTION_REVIEW.md` |

## 실기기에서 직접 확인할 항목

1. 기존 데이터가 있는 동일 앱에 업데이트 설치하고 기존 원본·거래·규칙·후보가 유지되는지 확인한다.
2. 수동 거래와 알림 거래를 각각 수정한다. 날짜/유형/금액/상대방/선택 계좌·잔액, 원본 보존, 수정 후 규칙 경고 변경을 확인한다.
3. 삭제 다이얼로그에서 취소/삭제를 확인한다. 삭제 시 연결 결과만 정리되고 Raw는 남아야 한다.
4. 중복 후보만 있을 때는 총액이 그대로이며 확정 후 한 거래만 제외되는지 확인한다. 상세의 제외 안내와 홈/월 목록 금액을 비교한다.
5. 환불 확정 전후 순지출과 총 입금을 확인한다. 월 경계의 환불은 입금 월에 반영되는지 확인한다.
6. 후보 아님 처리 후 경고가 사라지고 앱 재실행 후에도 유지되는지 확인한다.
7. 확정 거래를 수정해 재확인 표시가 생기고 이전 보정이 해제되는지 확인한다. 잔액만 수정하면 확정은 유지되어야 한다.
8. 겹치는 확정은 오류로 안내되고 중복 차감되지 않는지 확인한다.
9. 수정→상세→목록→홈의 시스템 뒤로가기, 삭제 후 목록 복귀, 홈 기본 종료 동작을 확인한다.
10. 화면 회전·키보드·다크 모드에서도 입력과 화면 상태를 확인한다.

한글 커밋 메시지 예시: `feat: 거래 편집 삭제와 정합성 검토 및 월 집계 반영`.
커밋은 실행하지 않았다.
