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
빌드 완료 후 EAS 빌드 페이지에서 AAB를 내려받아 Play Console 비공개 테스트 트랙에 올린다.
원격 versionCode를 자동 증가시키므로 후속 빌드도 같은 프로필을 사용한다.
