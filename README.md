# 금융 알림 기반 개인 지출 관리 시스템

Android에서 발생하는 금융 알림을 자동 수집하고, 비정형 알림을 표준 거래 이벤트로 정규화한 뒤
중복·취소·환불 등의 거래 정합성을 처리하고 사용자 정의 소비 규칙에 따라 피드백을 제공하는 프로젝트입니다.

## 프로젝트 목표

이 프로젝트의 핵심은 단순한 가계부 UI가 아니라 다음 세 가지 처리 로직을 직접 설계하고 구현하는 데 있습니다.

1. **정규화** — 금융 앱마다 다른 알림 형식을 공통 거래 이벤트로 변환
2. **정합성 처리** — 중복 알림, 승인 취소, 환불, 재처리 상황에서도 실제 거래 상태를 일관되게 유지
3. **사용자 정의 규칙 처리** — 사용자가 설정한 소비 조건을 거래 발생 시점에 평가하고 피드백 생성

## 현재 MVP 아키텍처

2026년 10월 MVP는 **Android 앱 내부 처리**를 기본 구조로 사용합니다.

```text
금융 앱 알림
    ↓
NotificationListenerService
    ↓
Raw Notification
    ↓
Room 로컬 저장
    ↓
알림 해석(Parser)
    ↓
정규화(Normalizer)
    ↓
NormalizedFinancialEvent
    ↓
거래 정합성 처리
    ↓
Transaction
    ↓
사용자 정의 규칙 처리
    ↓
Compose 가계부 / 피드백 UI
```

금융 알림 원문을 불필요하게 외부 서버로 보내지 않고, 수집·저장·정규화·정합성 처리·규칙 평가를 우선 온디바이스에서 수행합니다.

## 기술 스택

- **Platform**: Android
- **Language**: Kotlin
- **UI**: Jetpack Compose
- **Local Database**: Room / SQLite
- **Async**: Kotlin Coroutines
- **Test**: JUnit, Robolectric, Room in-memory database
- **Version Control**: GitHub

### Spring Boot 백엔드

초기 구조 검증 과정에서 Spring Boot 기반 health check와 원본 알림 수신 API를 구현했습니다.
현재 MVP에서는 백엔드 연결을 사용하지 않으며, 향후 다중 기기 동기화·클라우드 백업·웹 조회 등이 필요할 때 확장 대상으로 둡니다.

## 핵심 구현 범위

### 1. 금융 알림 수집
- Android `NotificationListenerService` 기반 알림 감지
- 원본 알림을 Room에 우선 저장
- 금융 알림 후보 선별 및 무의미한 알림 처리 정책 적용

### 2. 알림 정규화
- 금융 앱/알림 형식별 Parser 분리
- 금액, 가맹점, 거래 유형, 발생 시각, 출처 추출
- 공통 `NormalizedFinancialEvent` 모델로 변환
- 해석 실패 이벤트는 정상 거래로 강제 변환하지 않고 별도 상태로 관리

### 3. 거래 정합성 처리
- 동일 실제 거래의 다중 알림 중복 판별
- 승인 취소 / 환불과 원거래 연결
- 동일 이벤트 재처리 시 중복 생성 방지
- 거래 상태 관리

### 4. 사용자 정의 규칙 처리
예시:
- 편의점 월 지출 50,000원 이상 → 경고
- 배달 월 5회 이상 → 알림

규칙은 코드에 하드코딩하지 않고 데이터로 관리하는 구조를 목표로 합니다.

### 5. 가계부 및 피드백
- 거래 내역 조회
- 카테고리별 지출 확인
- 규칙 조건 충족 시 사용자 피드백

## 2026년 10월 목표

10월 안에 아래 흐름이 실제 Android 기기에서 끝까지 동작하는 Release Candidate 수준을 목표로 합니다.

```text
실제 금융 알림
→ 수집 및 Room 저장
→ 정규화
→ 정합성 처리
→ 거래 저장
→ 규칙 평가
→ 가계부 / 피드백
```

세부 일정은 [docs/weekly-plan.md](docs/weekly-plan.md), 진행률 기준은 [docs/ROADMAP.md](docs/ROADMAP.md)를 참고합니다.

## 저장소 구조

```text
.
├─ android/       # 현재 MVP Android 앱
├─ backend/       # 초기 Spring Boot 통신 PoC / 향후 확장 후보
├─ docs/          # 설계 및 개발 문서
└─ README.md
```

## 현재 상태

- 실제 기기에서 `NotificationListenerService` 알림 수신 확인
- Room 기반 Raw Notification 저장 계층 구현
- 동일 알림의 별도 행 저장 및 앱 재실행 후 데이터 유지 확인
- 다음 목표: **필요한 금융 알림 선별 → 첫 금융 알림 정규화**
