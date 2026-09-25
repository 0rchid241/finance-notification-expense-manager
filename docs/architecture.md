# 시스템 구조

## 목표 아키텍처

```text
[금융 앱]
    ↓ 알림
[Android Client]
    └─ NotificationListenerService
            ↓
      Raw Notification DTO
            ↓ REST
[Spring Boot Backend]
    ↓
[알림 해석]
    ↓
[정규화]
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
[Database]
    ↑
[Android 가계부 / 피드백 UI]
```

## 주요 책임

### Android Client
- 알림 접근 권한 안내
- NotificationListenerService를 통한 알림 감지
- 필요한 원본 알림 필드 추출
- 백엔드 API 전송
- 가계부와 피드백 표시

### Backend
- 금융 앱/알림 유형 식별
- 금융 앱별 알림 해석
- 표준 금융 이벤트로 정규화
- 중복 거래 판별
- 승인 취소/환불 원거래 매칭
- 거래 상태 관리
- 사용자 규칙 평가
- 거래/규칙/피드백 조회 API 제공

## 핵심 도메인 후보

### RawNotificationEvent
Android에서 전달된 원본 알림의 최소 정보.

### NormalizedFinancialEvent
금융 앱별 표현 차이를 제거한 표준 이벤트.

예상 필드:
- eventType
- amount
- merchant
- occurredAt
- source
- rawEventId

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

1. **해석과 비즈니스 로직 분리**
   - 금융 앱별 알림 문자열 처리 코드는 거래 정합성/규칙 처리와 분리한다.

2. **정규화 이후 공통 처리**
   - 금융 앱이 달라도 정규화 이후에는 같은 처리 흐름을 사용한다.

3. **불확실한 데이터 강제 확정 금지**
   - 중복 여부나 원거래 매칭이 불확실한 경우 잘못 합치는 것보다 확인 가능한 상태로 남기는 방향을 우선한다.

4. **테스트 가능한 핵심 로직**
   - 정규화, 중복 판별, 취소 매칭, 규칙 평가는 자동 테스트 가능한 순수 로직에 가깝게 설계한다.
