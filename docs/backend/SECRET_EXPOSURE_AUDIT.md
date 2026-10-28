# 저장소·로그 비밀값 노출 확인 (#605)

#600(카카오·애플 회원 인증)이 머지된 뒤, 새로 생긴 서버 비밀값과 토큰이 저장소·로그·API 응답에 남지 않는지 확인했다.
이 문서에는 실제 값을 적지 않는다. 값이 필요한 비교는 해시로만 했다.

| 대상 | 방법 | 결과 |
| -- | -- | -- |
| 저장소 현재 코드 | 설정 파일 검토, gitleaks `dir`, 패턴 grep | 실제 비밀값 0건 (발견 항목은 모두 테스트용 고정값·공개 키) |
| git 이력 | gitleaks `git` 전체 이력, #600 squash 커밋과 PR 원본 커밋 44개 | 실제 비밀값 0건 |
| 로그 | 로컬 서버를 dev 와 같은 JSON 로그로 띄워 인증 시나리오 19개 실행 후 검색 | 0건 |
| API 응답 | 같은 시나리오의 응답 본문·헤더에서 서버 비밀값 검색, 오류 응답 문구 확인 | 0건 |
| dev 서버 | 실제 계정으로 로그인·갱신·로그아웃·탈퇴, 잘못된 인가 코드, Refresh 재사용 후 Loki 검색 | 0건 |
| 서버 키 | dev·prod 앱 컨테이너의 `JWT_SECRET`·`OAUTH_TOKEN_ENCRYPTION_KEY` 를 공개 고정값 7개와 해시 비교 | 모두 다름 |

대상 비밀값: `JWT_SECRET`, `OAUTH_KAKAO_CLIENT_SECRET`, `OAUTH_KAKAO_ADMIN_KEY`, `OAUTH_APPLE_PRIVATE_KEY`,
`OAUTH_TOKEN_ENCRYPTION_KEY`, `OPENAI_API_KEY`, 서비스 Access/Refresh token, 카카오·애플 제공자 토큰·인가 코드.

## 1. 저장소 현재 코드

기준: `develop` `865a1be0` (2026-10-28)

### 설정 파일

`src/main/resources/application*.yml` 에서 대상 비밀값은 모두 `${ENV}` 참조이고 기본값이 없거나 비어 있다.

| 키 | 위치 | 기본값 |
| -- | -- | -- |
| `jwt.secret` | `application.yml` | 없음 (`${JWT_SECRET}`) |
| `oauth.token-encryption-key` | `application.yml` | 빈 값 |
| `oauth.kakao.client-secret`, `admin-key` | `application.yml` | 빈 값 |
| `oauth.apple.private-key` | `application.yml` | 빈 값 |
| `media.search.openai.api-key` | `application.yml` | 빈 값 |
| `jwt.secret` | `application-local.yml` | 로컬 개발용 고정값 있음 (local 프로필 전용) |

dev·prod 프로필은 `application.yml` 을 그대로 쓰고, 값은 서버의 `DEPLOY_PATH/.env` 에서
`docker compose --env-file .env` 로 들어간다. 배포 워크플로우는 이 값을 읽거나 출력하지 않는다.

### 스캐너와 패턴 검색

```bash
gitleaks dir . --redact          # gitleaks 8.30.1
grep -rnE 'BEGIN (EC |RSA )?PRIVATE KEY|client_secret|admin.key|KakaoAK' --include='*.java' --include='*.yml' .
```

gitleaks `dir` 은 53건을 냈다. 그중 git 이 추적하지 않는(`.gitignore`) 로컬 파일 41건을 빼면 12건이고, 모두 실제 비밀값이 아니다.

| 분류 | 위치 | 판단 |
| -- | -- | -- |
| 테스트 JWT 키 | `application-test.yml`, `TokenSubjectTypeTest`, 알림·다운로드·업로드 API 테스트 6개의 `jwt.secret=` | 테스트 전용 고정값 |
| CI JWT 키 | `.github/workflows/backend-ci.yml` | 주석대로 CI 테스트 전용 |
| 테스트 토큰 | `RequestTraceIntegrationTest` | 로그 마스킹 검증용 가짜 값 |
| 애플 개인키 | `AppleAuthClientTest:41` | 테스트가 실행 중 새로 만든 키를 PEM 으로 감싸는 코드. 키 원문은 없다 |
| PostHog 키 | `frontend/landing/analytics.js` | 브라우저에 싣도록 만든 공개 프로젝트 키(`phc_`). 서버 비밀값 아님 |
| R2 키 | `application-test.yml` | `dummy-*` |

추적하지 않는 로컬 파일(`backend/.env`, `backend/build/`, `backend/loadtest/fixtures/*.json`)은 저장소에 올라간 적이 없다.
`git log --all -- backend/.env 'backend/loadtest/fixtures/*'` 결과 `fixtures/.gitkeep` 추가 커밋만 있다.

위 항목은 모두 처음부터 공개용·테스트용으로 만든 값이라 교체할 필요가 없다. 다만 dev·prod 서버가 이 값을 그대로 쓰고 있다면 문제다.
커밋된 적 있는 고정 키는 7개다. 현재 코드의 6개(테스트·CI·local 기본값)와, 아래 이력 검사에서 나온 `feature/be-#601-app-push-fcm` 브랜치 `AppNotificationApiTest` 의 테스트 키 1개다.
로컬 `backend/.env` 의 `JWT_SECRET` 은 이 6개와 모두 달랐다. dev·prod 서버가 쓰는 값은 이 7개와 모두 달랐다([5절](#5-서버-키와-공개-고정값-비교)).

## 2. git 이력

```bash
gitleaks git . --redact --log-opts="--all"                       # 전체 이력: 951 commits scanned, 20건
gitleaks git . --redact --log-opts="88af3e35^..88af3e35"         # #600 squash 커밋: 1건
git fetch origin refs/pull/600/head:refs/probe605/pr600          # #600 PR 원본 커밋 44개
gitleaks git . --redact --log-opts="<merge-base>..refs/probe605/pr600"   # 40 commits scanned, 1건
```

- 전체 이력 20건은 위치 기준 12곳이다. 위 표와 같은 종류(테스트 JWT 키·CI 키·테스트 토큰·PostHog 키·`AppleAuthClientTest`)가 파일이 바뀔 때마다 다시 잡혔다. 현재 코드에 없는 위치는 두 곳이다.
  - `.github/workflows/ci.yml` 은 지금의 `backend-ci.yml` 로 이름이 바뀌기 전 파일이다. 같은 CI 키다.
  - `AppNotificationApiTest` 는 원격에 올라간 `feature/be-#601-app-push-fcm` 브랜치의 테스트 파일이다. 테스트 전용 키지만 위 6개와 다른 값이라 비교 목록에 넣었다.
- 이력에 커밋된 `DB_PASSWORD` 값은 로컬 기본값(`.env.example`·compose 기본값)뿐이다.
- #600 은 squash 머지라 `develop` 에는 커밋 하나만 있다. 원본 커밋은 브랜치가 지워졌어도 `refs/pull/600/head` 로 공개돼 있어 따로 받아 검사했다. 두 경우 모두 `AppleAuthClientTest:41` 1건뿐이다.
- 같은 범위를 `git log -p` 로 추가 확인했다. 추가된 줄에서 `KakaoAK <12자 이상>`, `sk-…`(OpenAI), JWT 원문, `MII…`(Base64 개인키) 패턴은 0건이다. `client_secret`·`KakaoAK`·`BEGIN PRIVATE KEY` 가 나온 줄은 모두 코드(`form.add("client_secret", …)`, `"KakaoAK " + adminKey`, PEM 머리 제거)와 테스트의 가짜 값(`secret`, `admin-key`)이다.

## 3. 로그와 API 응답

### 코드 경로 점검

로그에 비밀값이 들어갈 수 있는 경로를 #600 코드에서 따라가 봤다.

| 경로 | 확인 내용 |
| -- | -- |
| 외부 호출 실패 | `OAuthHttpClient` 는 제공자 오류를 `SocialAuthException` 으로 바꿀 때 응답 원문과 원인 예외를 넣지 않는다. 그래서 5xx 로그의 스택트레이스에도 제공자 응답이 없다 |
| 탈퇴 후 연결 해제 실패 | `WithdrawAccountService` 는 `memberId`, `socialAccountId` 만 남기고 예외는 넘기지 않는다 |
| 비밀값을 담는 객체 | `KakaoOAuthProperties`, `AppleOAuthProperties`, `SocialAuthentication`, `AuthResult`, `AuthResponse`, `EncryptedSocialTokens`, `RefreshRequest`, `SocialLoginRequest`, `SocialLoginStartResponse` 는 `toString()` 이 `[REDACTED]` 를 돌려준다 |
| 설정 오류 | 키 형식 오류 메시지(`AppleClientSecretGenerator`, `SocialTokenCipher`)에 값이 들어가지 않는다 |
| 요청 로그 | `RequestLoggingFilter` 는 메서드·경로·상태·시간만 남긴다. 헤더·쿼리스트링·본문은 남기지 않는다 |
| 로그 레벨 | dev 는 `com.sssok: debug`, `org.hibernate.SQL: debug` 다. `com.sssok` 의 debug 로그 4곳은 고정 문구나 `ErrorCode` 문구만 찍는다. SQL 로그는 바인딩 값 없이 `?` 로 나온다. 외부 HTTP 호출 로그(`org.springframework.web.client`, `org.apache.hc`)는 dev·prod 어디서도 켜지 않는다 |
| 2차 방어 | dev·prod JSON 로그는 `SensitiveValueMasker` 를 거쳐 `Bearer`, JWT, `token=`/`secret=` 꼴 값을 가린다 |
| 오류 응답 | `ErrorResponse` 메시지는 `ErrorCode` 에 정해 둔 문구다. 예상하지 못한 예외도 고정 문구로 내려간다 |
| actuator | `health,info,prometheus` 만 노출하고 `env`·`configprops` 는 열지 않는다 |

### 로컬 실행

| 항목 | 값 |
| -- | -- |
| 일시 | 2026-10-28 15:25:16 ~ 15:25:26 KST (같은 스크립트로 15:24:44 에 한 번 먼저 실행) |
| 대상 | 로컬 서버 (`develop` `865a1be0`, local 프로필 + `SssOkJsonLogFormatter`, `com.sssok: debug` 로 dev 와 같은 레벨) |
| 비밀값 | 이번 점검에서 새로 만든 가짜 값 (JWT 키, 암호화 키, 카카오 client secret·admin key, 애플 EC 개인키) |
| DB | pgvector Postgres 16 일회용 DB (확인 후 삭제) |
| 스크립트 | [evidence/605-secret-exposure/run-local.sh](evidence/605-secret-exposure/run-local.sh) |
| 결과 원본 | [evidence/605-secret-exposure/results-local.tsv](evidence/605-secret-exposure/results-local.tsv) |

로그는 dev 와 같은 JSON 형식으로 남겼다. 스크립트는 응답으로 받은 Access·Refresh token, Refresh 쿠키 값, state·loginVerifier,
보낸 인가 코드와 위조 토큰, 서버에 넣은 가짜 비밀값을 모두 모아 서버 로그에서 검색한다. 서버 비밀값은 응답 본문·헤더에서도 찾는다.
값이 실제로 검색되는지 보려고 요청 경로(`/api/v1/auth/refresh` 18줄, `kakao/login` 8줄)가 같은 로그에서 잡히는 것도 확인했다.

카카오·애플 완료 요청은 가짜 client id 로 실제 제공자 토큰 주소에 나갔다. 그래서 제공자의 오류 응답을 받는 경로(5xx 로그 포함)까지 실행됐다.

| ID | 시나리오 | 상태 |
| -- | -- | -- |
| L01 | 익명 인증 (Refresh 본문 전달) | 201 |
| L02 | 토큰 갱신 (본문) | 200 |
| L03 | 이미 쓴 Refresh 재사용 (중복 요청 유예 5초가 지난 6초 뒤) | 401 |
| L04 | 형식이 틀린 Refresh | 401 |
| L05 | 익명 인증 (Refresh 쿠키 전달) | 201 |
| L06 | 토큰 갱신 (쿠키) | 200 |
| L07 | CSRF 헤더 없는 쿠키 갱신 | 403 |
| L08 | Refresh 재사용으로 폐기된 세션의 로그아웃 | 401 |
| L09 | 위조 Bearer 토큰 | 401 |
| L10~L13 | 익명 인증 → 로그아웃 → 같은 Access 로 다시 로그아웃 → 같은 세션 Refresh 로 갱신 | 201, 204, 401, 401 |
| K01 | 카카오 로그인 시작 (익명 토큰 연결) | 200 |
| K02 | 카카오 로그인 완료 (잘못된 인가 코드, 제공자 오류) | 503 SOCIAL_AUTH_UNAVAILABLE |
| K03 | 카카오 로그인 완료 (이미 쓴 state) | 401 INVALID_SOCIAL_AUTH |
| P01 | 애플 로그인 시작 | 200 |
| P02 | 애플 로그인 완료 (잘못된 인가 코드) | 401 INVALID_SOCIAL_AUTH |
| W01 | 익명 회원 탈퇴 (탈퇴는 소셜 가입 회원만 가능) | 409 SOCIAL_AUTH_CONFLICT |

L08 이 401 인 것은 L03 에서 재사용이 감지돼 그 세션 전체가 폐기됐기 때문이다. 정상 로그아웃은 L10~L13 에서 확인했다.

| 검색 | 서버 로그 | 응답 |
| -- | -- | -- |
| 발급한 Access 5개·Refresh 3개·Refresh 쿠키 2개 | 0 | (응답으로 나가는 것이 정상) |
| state·loginVerifier (카카오·애플), 인가 코드 3개, 위조 토큰·형식이 틀린 Refresh | 0 | — |
| 서버 비밀값 5개 (JWT 키, 암호화 키, 카카오 client secret·admin key, 애플 개인키 본문) | 0 | 0 |
| 패턴 `Bearer `, `KakaoAK`, JWT 원문, `sssok_refresh=`, `BEGIN PRIVATE KEY`, `"access_token"`·`"refresh_token"`·`"id_token"`, `client_secret`, `?code=` | 0 | — |

카카오 시작 응답의 `authorizationUrl` 에는 client id·redirect uri·state 만 있고 client secret 은 없다.

### 로컬에서 하지 못한 시나리오

실제 계정이 필요한 카카오·애플 로그인 성공, 소셜 회원의 탈퇴와 외부 연결 해제는 로컬에서 실행하지 못했다. 이 부분은 아래 dev 실행에서 확인했다.

## 4. dev 서버

### 실행 환경

| 항목 | 값 |
| -- | -- |
| 일시 | 2026-10-28 16:50:51 ~ 16:58:41 KST, 로그 검색 범위 16:50:00 ~ 17:01:02 |
| 대상 | dev (`https://dev-api.ssssok.com`, 웹 `https://dev.ssssok.com/app`) |
| 로그 | 중앙 Grafana(`monitor.ssssok.com`) Loki, `{environment="dev"}` 4,730줄 |
| 계정 | 카카오·애플 테스트 계정 각 1개 (점검 후 탈퇴), 익명 계정 2개 |

dev 는 카카오·애플 로그인이 켜져 있고 콜백은 `https://dev.ssssok.com/app/auth/callback/{kakao|apple}` 이다.

### 시나리오

카카오·애플 로그인과 탈퇴는 dev 웹에서 실제 계정으로 했다. Refresh 재사용과 잘못된 인가 코드는 API 를 직접 호출했다.
잘못된 인가 코드는 로그인을 시작해 받은 state·loginVerifier 에 가짜 code 를 붙여 완료 API 를 불렀다. 서버는 이 코드를 실제 제공자 토큰 주소로 보낸다.
시각은 dev 요청 로그 기준이다.

| 시각 | 시나리오 | 결과 |
| -- | -- | -- |
| 16:50:53 | 익명 인증 → 토큰 갱신(본문) | 201 → 200 |
| 16:50:59 | 이미 쓴 Refresh 재사용 (6초 뒤) · 형식이 틀린 Refresh · 위조 Bearer 토큰으로 로그아웃 | 401 · 401 · 401 |
| 16:50:59 ~ 16:51:00 | 익명 인증 → 로그아웃 → 같은 세션 Refresh 로 갱신 | 201 → 204 → 401 |
| 16:53:54 | 카카오 로그인 (신규 가입) | 200 |
| 16:54:02 | 로그아웃 | 204 |
| 16:54:36 | 애플 로그인 | 200 |
| 16:56:49 | 웹 토큰 갱신 | 200 |
| 16:57:14 | 로그아웃 | 204 |
| 16:57:38 | 카카오 완료: 가짜 인가 코드 · 이미 쓴 state | 401 INVALID_SOCIAL_AUTH · 401 INVALID_SOCIAL_AUTH |
| 16:57:39 | 애플 완료: 가짜 인가 코드 · 이미 쓴 state | 401 INVALID_SOCIAL_AUTH · 401 INVALID_SOCIAL_AUTH |
| 16:58:21 → 16:58:24 | 카카오 로그인 → 탈퇴 (어드민 키로 연결 해제) | 200 → 204 |
| 16:58:37 → 16:58:41 | 애플 로그인 → 탈퇴 (보관 토큰으로 revoke) | 200 → 204 |

애플 로그인 화면은 앱 내장 브라우저에서 "문제가 발생했습니다"를 여러 번 띄웠다. 애플 계정 페이지 쪽 오류라 dev 서버에는 요청이 오지 않았고, 다시 시도해 성공했다.

### 로그 검색

검색이 실제로 걸리는지는 같은 범위의 요청 로그로 먼저 확인했다. 탈퇴 요청(`DELETE /api/v1/auth/account`, OPTIONS 포함) 4줄, 소셜 로그인 완료 요청 18줄이 잡혔다.

| 검색 (LogQL 줄 필터) | 건수 |
| -- | -- |
| JWT 원문 `eyJ….….` | 0 |
| `(?i)bearer [A-Za-z0-9]` | 0 |
| `(?i)cookie` | 0 |
| `(?i)kakaoak\|client_secret\|refresh_token\|access_token\|id_token\|sssok_refresh=` (SQL 로그 제외) | 0 |
| `BEGIN (EC \|RSA )?PRIVATE KEY` | 0 |
| 인가 코드 `(?i)[?&]code=[^*&]` | 0 |
| R2 서명 `x-amz-signature=`, SSE 토큰 `[?&]token=`, `jdbc:` | 0 · 0 · 0 |
| 보낸 가짜 인가 코드·위조 토큰 (`probe605`) | 0 |
| 받은 Refresh token 3개, state·loginVerifier 4개 (값의 24자 조각) | 0 · 0 |
| 탈퇴 후 연결 해제 실패 기록 (`탈퇴 후 소셜 연결 정리 미완료`) | 0 |

- 위 키워드 질의를 SQL 로그까지 넣어 돌리면 43건이 나온다. 모두 `org.hibernate.SQL` 의 테이블·컬럼 이름(`refresh_token`, `encrypted_refresh_token` 등)이다. 바인딩 값은 `?` 로, 네이티브 쿼리 대입 값은 `***` 로 나왔다.
- SQL 로그에서 `= '값'` 꼴은 2,942줄이었다. 값은 `PROCESSING`·`READY`·`PENDING`·`RUNNING` 상태값 4가지뿐이다. 백그라운드 작업의 상태 조회 쿼리다.
- 같은 범위의 WARN·ERROR(SQL 제외)는 7건이다. Hibernate 매핑 경고(HHH000502) 6건과, 실시간 구독(SSE) 연결이 끊긴 뒤 하트비트에서 난 `Broken pipe` ERROR 1건이다. 인증과 무관하고 민감값도 없다.
- 탈퇴 두 건 모두 연결 해제 실패 기록이 없었다. 카카오·애플 쪽 연결 해제가 성공했다는 뜻이다.

### API 응답

- 오류 응답은 모두 `ErrorCode` 문구(`UNAUTHORIZED`, `INVALID_SOCIAL_AUTH`)뿐이다. 제공자 응답 원문이나 토큰은 섞이지 않았다.
- 시작 응답의 `authorizationUrl` 에는 client id·redirect uri·state·nonce 만 있다. client secret 은 없다.

## 5. 서버 키와 공개 고정값 비교

저장소 이력에 커밋된 고정 키 7개(테스트·CI·local 기본값)를 dev·prod 가 쓰고 있지 않은지 확인했다.

| 항목 | 값 |
| -- | -- |
| 일시 | 2026-10-28 17:10 ~ 17:15 KST |
| 대상 | `[DEV] sssOK` (`i-0e2623f850fd03568`, 컨테이너 `sssok-app-dev`), `[PROD] sssOK` (`i-0894ef01441a14219`, 컨테이너 `sssok-app`) |
| 접속 | AWS 콘솔 Systems Manager Session Manager (읽기 전용 명령만 실행, 확인 후 세션 종료) |

`.env` 파일 대신 실행 중인 앱 컨테이너의 환경 변수를 비교했다. 앱이 실제로 쓰는 값이기 때문이다.
값은 컨테이너 밖으로 파이프로만 넘겨 SHA-256 으로 바꾸고, 화면에는 비교 결과만 찍었다.
비교 대상 해시 7개는 [check-dev-server.sh](evidence/605-secret-exposure/check-dev-server.sh) 의 `PUBLIC_KEY_HASHES` 와 같다.

```bash
for n in JWT_SECRET OAUTH_TOKEN_ENCRYPTION_KEY; do
  h=$(sudo docker exec <앱 컨테이너> sh -c "printf %s \"\$$n\"" | sha256sum | cut -c1-64)
  # 빈 값의 해시면 EMPTY, 공개 고정값 해시 7개 중 하나면 SAME_AS_PUBLIC, 아니면 DIFFERENT
done
```

| 서버 | `JWT_SECRET` | `OAUTH_TOKEN_ENCRYPTION_KEY` |
| -- | -- | -- |
| dev | DIFFERENT (공개 고정값과 다름) | DIFFERENT (공개 고정값과 다름) |
| prod | DIFFERENT (공개 고정값과 다름) | DIFFERENT (공개 고정값과 다름) |

dev·prod 모두 저장소에 공개된 키를 쓰지 않는다. 두 값 모두 비어 있지도 않다.

## 발견 사항과 교체 필요 여부

| 발견 | 위치 | 교체 필요 |
| -- | -- | -- |
| 테스트·CI·local 고정 키 7개, 테스트 토큰, 테스트 애플 키 생성 코드 | 저장소 코드·이력 | 불필요 (처음부터 공개된 테스트 값이고, dev·prod 는 이 값을 쓰지 않는다) |
| PostHog 프로젝트 키 | `frontend/landing/analytics.js` | 불필요 (브라우저에 싣는 공개 키) |

## 확인하면서 알게 된 점

- `SensitiveValueMasker` 는 `KakaoAK <키>` 나 JWT 가 아닌 제공자 토큰(카카오 Access/Refresh)처럼 접두사·키 이름 없이 나온 값은 가리지 못한다. 지금은 그런 값을 로그에 남기는 코드가 없어서(1차 방어) 문제는 없다. 외부 호출을 DEBUG 로 찍는 설정(`org.springframework.web.client` 등)을 켜면 새어 나갈 수 있으니 dev·prod 에서는 켜지 않는다.
