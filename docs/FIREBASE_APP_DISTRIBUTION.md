# Firebase App Distribution 외부 테스트 설정

외부에서 Android Studio 없이 Dev APK를 설치·업데이트하기 위한 설정이다.

## 목적

- 본 개발앱(`com.orchid241.financenotificationmanager`)과 별개로 외부 테스트용 Dev 앱을 배포한다.
- Dev 패키지명: `com.orchid241.financenotificationmanager.dev`
- GitHub Actions에서 `externalDebug` APK를 빌드한다.
- Firebase 설정이 완료되면 빌드 성공 후 Firebase App Distribution으로 자동 배포한다.
- Firebase 설정 전에도 GitHub Actions artifact 업로드는 계속 동작한다.

## 최초 1회 설정

### 1. Firebase 프로젝트 생성

Firebase Console에서 새 프로젝트를 만든다. 별도의 Firebase 기능은 지금 필요 없다.

### 2. Android 앱 등록

Android 패키지 이름은 반드시 아래 값으로 등록한다.

```text
com.orchid241.financenotificationmanager.dev
```

앱 등록 후 Firebase의 일반 설정에서 Firebase App ID를 확인한다.
형태 예시는 다음과 같다.

```text
1:1234567890:android:abcdef123456
```

### 3. App Distribution 시작

Firebase Console의 **App Distribution** 메뉴에서 Dev 앱을 선택하고 시작한다.
테스터로 실제 설치에 사용할 Google 계정 이메일을 추가한다.

### 4. CI용 서비스 계정 준비

Google Cloud Console에서 Firebase 프로젝트용 서비스 계정을 만들고 JSON 키를 발급한다.
CI에서는 이 JSON을 파일로 커밋하지 않고 GitHub Secret으로만 보관한다.

### 5. GitHub Actions Secrets 등록

Repository → Settings → Secrets and variables → Actions에 아래 3개 Secret을 추가한다.

```text
FIREBASE_APP_ID
FIREBASE_SERVICE_ACCOUNT_JSON
FIREBASE_TESTERS
```

값:

- `FIREBASE_APP_ID`: Firebase Android App ID
- `FIREBASE_SERVICE_ACCOUNT_JSON`: 서비스 계정 JSON 파일 전체 내용
- `FIREBASE_TESTERS`: 테스트에 사용할 이메일. 여러 명이면 쉼표로 구분

예:

```text
user@example.com
```

## 자동 배포 흐름

```text
GitHub push
→ testDebugUnitTest
→ assembleExternalDebug
→ GitHub artifact 업로드
→ Firebase CLI 설치
→ Firebase App Distribution 업로드
→ 테스터에게 새 버전 제공
```

Firebase Secret이 아직 등록되지 않은 경우 Firebase 배포 단계만 자동으로 건너뛰며, 테스트와 APK 빌드는 계속 수행된다.

## 실사용 시

최초 초대 메일에서 테스터 등록을 한 번 완료하면 이후 새 빌드는 Firebase App Distribution에서 받아 설치하면 된다.
Dev 앱은 본앱과 패키지가 다르므로 두 앱을 동시에 설치할 수 있다.

Dev 앱은 별도 앱이므로 알림 접근 권한과 Room 데이터도 본앱과 따로 관리된다.
