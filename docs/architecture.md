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
[알림 해석 Parser]
    ↓
[정규화 Normalizer]
    ↓
NormalizedFinancialEvent
    ↓
[거래 정합성 처리]
    ↓
Transaction
    ↓
[사용자 정의 규칙 처리]
    ↓
Feedback
    ↓
[Compose 가계부 / 피드백 UI]
```

## 주요 책임

### 알림 수집 계층
- 알림 접근 권한 안내
- `NotificationListenerService`를 통한 알림 감지
- 원본 알림 필드 추출
- Room에 원본 이벤트 우선 저장
- 금융 알림 후보 선별

### 데이터 계층
- Room을 이용한 로컬 영속 저장
- Raw Notification, 정규화 이벤트, 거래, 규칙, 피드백 데이터 관리
- Repository를 통해 서비스/도메인 로직과 DAO 직접 결합 방지

### 알림 해석 / 정규화 계층
- 금융 앱/알림 형식 식별
- 형식별 Parser로 금액, 가맹점, 거래 유형, 발생 시각, 출처 추출
- 공통 `NormalizedFinancialEvent`로 변환
- 해석 실패 또는 불확실한 이벤트를 별도 상태로 유지

### 거래 정합성 계층
- 동일 실제 거래의 다중 알림 중복 판별
- 승인 취소/환불 원거래 매칭
- 동일 이벤트 재처리 방지
- 거래 상태 관리

### 규칙 처리 계층
- 사용자 정의 소비 규칙 저장 및 평가
- 거래 반영 후 활성 규칙 평가
- 조건 충족 시 Feedback 생성

### UI 계층
- 거래 목록과 상세 상태 표시
- 카테고리별 지출 요약
- 사용자 규칙 관리
- 피드백 표시
- 필요한 경우 잘못 처리된 거래 확인/수정 흐름 제공

## 핵심 도메인 후보

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

원본 단계에서는 같은 알림이 여러 번 들어와도 임의로 중복 제거하지 않는다.

### NormalizedFinancialEvent
금융 앱별 표현 차이를 제거한 표준 이벤트.

예상 필드:
- eventType
- amount
- merchant
- occurredAt
- source
- rawNotificationId

### Transaction
사용자 가계부에 반영되는 실제 거래.

예상 상태:
- APPROVED
- CANCELLED
- REFUNDED

### Rule
사용자 소비 관리 조건.

예상 필드:
- period
- category
- metric
- operator
- threshold
- action
- enabled

### Feedback
규칙 충족 결과로 생성되는 사용자 피드백.

## 설계 원칙

1. **원본 우선 보존**
   - 수집 직후 Room에 Raw Notification을 저장하고, 이후 처리 실패가 원본 손실로 이어지지 않도록 한다.

2. **해석과 비즈니스 로직 분리**
   - 금융 앱별 문자열 처리 코드는 거래 정합성/규칙 처리와 분리한다.

3. **정규화 이후 공통 처리**
   - 금융 앱이 달라도 정규화 이후에는 같은 처리 흐름을 사용한다.

4. **불확실한 데이터 강제 확정 금지**
   - 중복 여부나 원거래 매칭이 불확실한 경우 잘못 합치는 것보다 확인 가능한 상태로 남기는 방향을 우선한다.

5. **테스트 가능한 핵심 로직**
   - 정규화, 중복 판별, 취소 매칭, 규칙 평가는 UI와 분리하고 자동 테스트 가능한 형태로 설계한다.

6. **서버는 선택적 확장**
   - 2026년 10월 MVP는 서버를 요구하지 않는다.
   - Spring Boot 코드는 초기 통신 PoC로 보존하며, 향후 동기화·백업·웹 조회가 필요할 때 확장한다.
