# 쏙 WebView 앱

기존 React 웹을 Expo 네이티브 앱의 WebView에서 실행한다. 웹 UI를 React Native로 복제하지 않는다.
웹과 별도 package.json, pnpm workspace 및 lockfile을 사용한다.
`index.ts` → `App.tsx`에서 WebView 하나를 실행하며, 화면 이동은 웹의 React Router가 담당한다.
Expo Router와 네이티브 Stack은 사용하지 않는다.

## 실행

```sh
cd frontend/mobile
pnpm install
pnpm start
```

터미널의 QR 코드를 **SDK 58을 지원하는 Expo Go**로 연다. 설치된 Expo Go가 SDK 58을 지원하지 않으면 호환 버전이나 development build가 필요하다.
iOS 시뮬레이터는 Xcode, Android 에뮬레이터는 Android SDK가 필요하다.
`pnpm ios` / `pnpm android`는 해당 환경에서 Metro와 시뮬레이터·에뮬레이터를 실행한다.

기본 진입점은 `https://www.ssssok.com/home`이다. 로그인 화면은 별도 브랜치 작업으로 아직 포함하지 않는다.

## 로컬 웹 연결

`.env.example`을 `.env.local`로 복사하고 실제 기기에서 접근 가능한 웹 주소를 지정한다.

```dotenv
EXPO_PUBLIC_WEB_URL=http://192.168.0.10:3000/home
```

웹 개발 서버를 별도 터미널에서 실행하고 같은 Wi-Fi를 사용한다. `localhost`는 개발 PC가 아니라 앱을 실행하는 기기를 가리킨다.
웹 개발 서버의 포트와 host 설정을 확인한다. 로컬 HTTP는 개발 모드에서만 허용하며, 배포에는 HTTPS를 사용한다.
변경 후 Metro를 재시작한다. `EXPO_PUBLIC_*` 값은 공개 번들에 포함되므로 비밀 값을 넣지 않는다.
네이티브 빌드에서 HTTP가 OS 정책에 차단되면 HTTPS 개발 주소를 사용한다.

## 현재 범위

- Safe Area와 상태 표시줄
- WebView의 기존 웹·익명 인증·localStorage 이용
- Android 뒤로 가기는 웹 히스토리 우선, 더 돌아갈 곳이 없으면 기본 앱 종료 동작
- 동일 origin에서 탐색하고 외부 HTTP(S) 링크는 브라우저로 열기
- 초기 로딩, 연결 실패 및 WebView 프로세스 종료 시 재시도

앱 WebView의 localStorage는 일반 브라우저와 공유되지 않는다.
소셜 로그인 SDK, 인증 Bridge, Universal Links/App Links, 네이티브 사진 저장은 후속 작업이다.
파일 선택·업로드, 대량 사진, 영상 재생, 다운로드와 공유의 실기기 검증은 별도로 필요하다.
현재 다운로드를 사진 앱에 저장하는 기능은 구현하지 않았다.

## 검증

```sh
pnpm type-check
pnpm lint
pnpm exec expo install --check
pnpm exec expo export --platform ios --output-dir /tmp/sssok-mobile-ios
pnpm exec expo export --platform android --output-dir /tmp/sssok-mobile-android
```

export는 JS 번들 검증이며 네이티브 컴파일이나 실기기 실행을 대신하지 않는다.

## Google Play 비공개 테스트용 AAB

패키지명은 `com.ssssok.app`이다. production 프로필은 운영 웹을 열고 store 배포용 AAB를 생성한다.
비공개 테스트도 동일한 AAB를 사용하며, 이 명령은 Google Play에 자동 제출하지 않는다.

```sh
npx eas-cli@latest login
npx eas-cli@latest build --platform android --profile production
```

첫 실행 시 Expo 프로젝트 연결과 Android 업로드 키 생성·선택이 필요하다.
Google Play에 이미 등록된 앱이면 기존 패키지명과 업로드 키를 사용한다.
기존 앱을 EAS 원격 버전 관리로 처음 전환할 때는 빌드 전에 `npx eas-cli@latest build:version:set`을 실행하고, Android의 원격 versionCode를 Play Console에 이미 업로드한 가장 높은 값으로 맞춘다. 다음 production 빌드에서 이 값이 자동 증가한다. 이미 동기화된 프로젝트는 다시 설정할 필요가 없다.
빌드 완료 후 EAS 빌드 페이지에서 AAB를 내려받아 Play Console 비공개 테스트 트랙에 올린다.
원격 versionCode를 자동 증가시키므로 후속 빌드도 같은 프로필을 사용한다.

## 방 알림 개발 빌드

앱 푸시는 Firebase Cloud Messaging을 사용합니다. Expo Go에는 Firebase 네이티브 모듈이 없으므로 실기기 개발 빌드에서 확인합니다.

1. 개발 Firebase 프로젝트에서 `com.ssssok.app`에 해당하는 iOS·Android 앱 설정 파일을 받습니다. iOS에는 Firebase 콘솔의 APNs 인증 키도 설정되어 있어야 합니다.
2. Firebase 앱 설정 파일 4개를 `firebase/`에 둡니다. `app.config.js`가 기본 및 development 빌드에서는 dev 파일, production 빌드에서는 prod 파일을 선택합니다. `APP_ENV=production`으로 로컬에서도 운영 설정을 선택할 수 있습니다. 파일은 Git에서 제외하고 저장소 루트의 `.easignore`에서 EAS 빌드 업로드에 포함합니다. 다른 팀원이 빌드할 때는 파일 4개를 별도로 받아야 합니다. EAS File 환경 변수 `GOOGLE_SERVICES_INFO_PLIST`, `GOOGLE_SERVICES_JSON`을 지정하면 해당 경로가 우선합니다. 운영·개발 앱 파일은 웹의 API 서버 FCM 프로젝트와 일치해야 합니다. 서버 서비스 계정 키는 앱 빌드에 넣지 않습니다.
3. `npx eas-cli@latest build --profile development --platform ios` 또는 `--platform android`로 빌드합니다. 개발 빌드를 설치한 뒤 `npx expo start --dev-client`로 실행합니다. 서버가 dev 웹에 이 브랜치의 웹 코드를 제공하거나, 로컬 웹을 실기기에서 접속할 수 있게 `EXPO_PUBLIC_WEB_URL`을 맞춰야 합니다.

회원 로그인 후 기기 알림 권한을 허용하고 같은 방에 두 계정으로 들어갑니다. 방 설정에서 사진을 요청하거나 사진을 올립니다. 받는 앱이 방을 보고 있으면 사진은 기존 실시간 갱신으로 보이고, 사진 요청은 앱 안에 안내가 뜹니다. 받는 앱이 백그라운드에 있으면 시스템 알림을 눌러 해당 방으로 이동할 수 있습니다. 방 알림을 끄거나 로그아웃한 기기에는 이후 알림이 오지 않는지 확인합니다.

### 현재 방에 따른 알림 표시

웹 라우터가 보고 있는 방 코드를 WebView 브릿지로 앱에 전달합니다. 앱이 같은 방을 보고 있으면 기존 화면 내 안내를 사용하고, 다른 방·방 목록·홈을 보고 있으면 Notifee로 시스템 알림을 표시합니다. 백그라운드에서는 기존 FCM 알림을 그대로 사용합니다. 알림을 누르면 서버의 data.url을 검증한 뒤 해당 방으로 이동합니다.

Notifee가 포함되지 않은 기존 APK에서는 다른 방의 요청도 기존 화면 안내로 표시합니다. 이 분기를 시스템 알림으로 확인하려면 development APK를 한 번 다시 빌드·설치해야 합니다.
