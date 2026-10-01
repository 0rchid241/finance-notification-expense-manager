# 시스템 구조

## 목표 아키텍처

현재 MVP는 서버 의존 없이 **Android 앱 내부에서 핵심 처리를 완료**하는 구조를 사용한다.

```text
[금융 앱]
    ↓ 알림
[NotificationListenerService]
    ↓
[Raw Notification]
    ↓
[Room Raw 저장]
    ↓
[금융 앱별 Parser]
    ↓
[구조화된 금융 거래]
    ↓
[거래 정합성 처리]
    ↓
[사용자 정의 규칙 처리]
    ↓
[Compose 가계부 / 피드백 UI]
```

## 현재 구현 흐름 — 2026-10-01

현재는 카카오뱅크 입금/출금 알림을 다음 흐름으로 처리한다.

```text
com.kakaobank.channel
    ↓
SupportedFinancialApps
    ↓
FinanceNotificationListenerService
    ↓
FinancialTransactionRepository
    ├─ RawNotificationRepository → raw_notifications
    └─ KakaoBankNotificationParser
            ↓
      financial_transactions
            ↓
      Raw 상태 PROCESSED
            ↓
      DAO Flow
            ↓
      Compose 최근 거래 카드
```

파싱 실패 시 거래는 생성하지 않고 Raw 상태를 `PARSE_FAILED`로 남긴다.
구조화 거래 저장과 `PROCESSED` 상태 변경은 하나의 Room 트랜잭션으로 처리한다.

## 주요 책임

### 알림 수집 계층
- 알림 접근 권한 안내
- `NotificationListenerService`를 통한 알림 감지
- 지원 금융 앱 패키지 선별
- 원본 알림 필드 추출
- Room에 원본 이벤트 우선 저장

### 데이터 계층
- Room을 이용한 로컬 영속 저장
- Raw Notification과 구조화 거래 관리
- Repository를 통해 서비스/도메인 로직과 DAO 직접 결합 방지
- 명시적 Room Migration으로 기존 데이터 보존

### 알림 해석 / 정규화 계층
- 금융 앱/알림 형식 식별
- 형식별 Parser로 거래 정보 추출
- 현재 카카오뱅크에서 추출하는 값:
  - 입금 / 출금
  - 금액
  - 상대방
  - 계좌 끝 4자리
  - 잔액
  - 발생 시각
  - 출처
- 해석 실패 또는 불확실한 이벤트를 정상 거래로 강제 변환하지 않음

### 거래 정합성 계층 — 이후 구현
- 동일 실제 거래의 다중 알림 중복 판별
- 내부이체 후보 처리
- 승인 취소/환불 원거래 매칭
- 동일 이벤트 재처리 방지
- 거래 상태 관리

### 규칙 처리 계층 — 이후 구현
- 사용자 정의 소비 규칙 저장 및 평가
- 거래 반영 후 활성 규칙 평가
- 조건 충족 시 Feedback 생성

### UI 계층
- 현재: 최신 구조화 거래를 Compose 카드 목록으로 표시
- 이후:
  - 거래 상세 / 상태 표시
  - 카테고리별 지출 요약
  - 사용자 규칙 관리
  - 피드백 표시
  - 잘못 처리된 거래 확인/수정

## 핵심 데이터

### RawNotification

Android에서 수집한 원본 알림 이벤트.

현재 주요 필드:
- id
- notificationKey
- packageName
- title
- text
- postedAt
- receivedAt
- processingStatus

처리 상태:
- `PENDING` — 아직 처리 완료되지 않음
- `PROCESSED` — 구조화 거래 생성 완료
- `PARSE_FAILED` — 현재 Parser로 해석하지 못함

원본 단계에서는 같은 알림이 여러 번 들어와도 임의로 중복 제거하지 않는다.

### 구조화된 금융 거래

현재 `financial_transactions`에 저장되는 거래 데이터.

주요 필드:
- rawNotificationId
- transactionType
- amount
- counterparty
- accountLast4
- balance
- source
- occurredAt
- createdAt

현재 이 데이터는 **정규화된 입력 결과**이며, 중복·내부이체·취소·환불 같은 거래 정합성 판단은 아직 적용하지 않는다.

### Rule

사용자 소비 관리 조건. 이후 구현한다.

예상 필드:
- period
- category
- metric
- operator
- threshold
- action
- enabled

### Feedback

규칙 충족 결과로 생성되는 사용자 피드백. 이후 구현한다.

## 설계 원칙

1. **원본 우선 보존**
   - 수집 직후 Room에 Raw Notification을 저장하고, 이후 처리 실패가 원본 손실로 이어지지 않도록 한다.

2. **해석과 비즈니스 로직 분리**
   - 금융 앱별 문자열 처리 코드는 거래 정합성/규칙 처리와 분리한다.

3. **정규화 이후 공통 처리**
   - 금융 앱이 달라도 정규화 이후에는 같은 처리 흐름을 사용한다.

4. **불확실한 데이터 강제 확정 금지**
   - 해석 실패, 중복 여부, 원거래 매칭이 불확실한 경우 잘못 확정하는 것보다 확인 가능한 상태로 남기는 방향을 우선한다.

5. **테스트 가능한 핵심 로직**
   - Parser, 정합성 처리, 규칙 평가는 UI와 분리하고 자동 테스트 가능한 형태로 설계한다.

6. **서버는 선택적 확장**
   - 2026년 10월 MVP는 서버를 요구하지 않는다.
   - Spring Boot 코드는 초기 통신 PoC로 보존하며, 향후 동기화·백업·웹 조회가 필요할 때 확장한다.
