# 소셜 계정 및 제공자 인증 연동 (#595)

## 현재 구현 범위

소셜 계정 저장, 카카오·애플 코드 교환 및 사용자 검증, 로그인 시도의 메모리 저장·일회성 소비를 구현했다. 카카오·애플 시작·완료 API와 신규 가입·기존 회원 로그인을 연결했다. 기존 계정에 익명 활동을 통합하는 기능도 연결했다. 기본 제공자 설정은 비활성화다. 실제 제공자 계정 로그인도 미검증이다.

OAuth 로그인 진행 정보는 **쿠키 없는 단일 서버 메모리 캐시**로 관리한다. 로그인 완료 후 서비스 refresh token은 웹 쿠키 또는 앱 응답 본문으로 전달한다. 이전 HttpSession·쿠키 계약을 대체한다. 프론트 코드는 수정하지 않는다. 웹·앱은 같은 시작·완료 API 계약을 사용하되, 네이티브 SDK의 인증 결과 형식·클라이언트 ID·리다이렉트는 앱 연동 단계에서 따로 검증한다.

## 코드 읽는 순서

| 클래스 | 역할 |
| --- | --- |
| `OAuthLoginAttemptService` | 시작 인증 확인, 무작위 값 생성, 요청 검증·소비 |
| `OAuthLoginAttempt` | 저장 데이터와 생성·만료 규칙 |
| `outgoing/cache/auth/OAuthLoginAttemptRepository` | Caffeine 메모리 저장·조회·조건부 제거 |
| `SocialAuthClient` | 제공자 공통 인증 계약 |
| `KakaoAuthClient` | 코드 교환 → 사용자 정보 조회 |
| `AppleAuthClient` | 코드 교환 → ID 토큰 검증 |
| `OAuthHttpClient` | HTTP 호출과 외부 오류 변환 |
| `AppleSigningKeyProvider` | 애플 서명 공개키 조회·캐시 |
| `AppleClientSecretGenerator` | 애플 요청용 client secret 생성 |
| `SocialAccount` / `SocialIdentity` | 우리 회원과 제공자 사용자 식별자의 연결 |

서비스 → 저장소 구조와 도메인 모델을 유지한다. 임시 데이터 저장소는 DB를 사용하지 않으므로 JPA 계층이 없다. 기존 회원·소셜 계정 저장 구조에는 변경이 없다.

## 로그인 시도 메모리 저장

- 시작 시 SecureRandom 32바이트로 state·loginVerifier·애플 nonce를 각각 생성한다. 카카오는 nonce를 사용하지 않는다.
- 서버에는 state·loginVerifier의 SHA-256 해시, 제공자, 설정된 리다이렉트, 익명 회원 ID, nonce, 생성·만료 시각을 저장한다.
- 익명 회원은 시작 요청의 서비스 토큰을 검증해서 확인한다. memberId를 외부 입력으로 받지 않는다. 인증 없이 시작하면 익명 활동을 연결하지 않는다.
- 제공자 비활성, 잘못된 토큰, 등록·통합·탈퇴 회원을 익명 연결 대상으로 지정하는 요청은 거부한다.
- 기존 의존성인 Caffeine으로 최대 10,000건, 쓰기 후 5분 만료 정책을 사용한다. 조회나 잘못된 확인값은 유효시간을 늘리지 않는다. 도메인 만료 시각도 소비 직전에 검사한다.
- 최대 크기는 캐시의 제거 정책이다. 한도 도달 시 일부 요청이 만료 전에 제거될 수 있고 사용자는 다시 로그인해야 한다. 정리 작업은 Caffeine의 systemScheduler와 유지보수에 맡긴다.
- 소비 시 불변 저장값을 확인한 뒤 `asMap().remove(key, expectedValue)`로 동일한 값이 남아 있을 때만 원자적으로 제거한다. 동시에 요청해도 하나만 성공하며, 잘못된 확인값·제공자는 정상 요청을 지우지 않는다.
- 회원 상태 확인에는 기존 DB 트랜잭션을 사용한다. 캐시 소비는 DB 트랜잭션과 독립적이어서 후속 가입 실패나 DB 롤백으로 복원되지 않는다.
- 서버 재시작·캐시 제거 후 이전 요청은 거부한다. 배포 중 진행 중인 로그인은 다시 시작한다. 기존 서비스 인증 토큰에는 영향이 없다.
- 서버가 여러 프로세스·인스턴스로 늘어나면 공유 저장소를 재검토한다. 이 구현은 단일 JVM 전제다.

## 카카오 API 계약 — 시작·가입·로그인·활동 통합 구현

시작·완료 API를 코드에 연결했다. 기존 계정 + 익명 활동 통합 요청은 기존 회원에게 활동을 이전하고 해당 회원의 서비스 토큰을 발급한다. 실제 서비스 로그인 공개 전 연동 검증이 필요하다. 기존 `ApiResponse<AuthResponse>`와 Authorization 인증을 재사용한다. OAuth 진행에는 HttpSession을 사용하지 않으며, 서비스 refresh token 전달에는 별도 쿠키를 사용한다.

### 전체 흐름

1. 웹·앱이 시작 API를 호출한다. 익명 활동 연결이 필요하면 현재 익명 토큰을 전달한다.
2. 서버가 로그인 URL, state, loginVerifier, 만료 시각을 반환한다.
3. 웹·앱은 loginVerifier를 임시 보관하고 카카오 인증으로 이동한다. 확인값은 URL·카카오 요청에 넣지 않는다.
4. 콜백의 code·state와 시작할 때 보관한 loginVerifier를 완료 API에 전달한다.
5. 서버가 요청을 검증·소비하고 제공자 사용자 확인, 가입·로그인·익명 활동 통합 후 서비스 토큰을 반환한다.

웹에서는 동일 origin의 sessionStorage 등으로 임시 보관할 수 있다. 앱은 로그인 시작을 담당한 클라이언트에 보관한다. 이것은 연동 계약이며 이번 작업에서 클라이언트 코드를 구현하지 않는다. 콜백 URL에서 전달된 loginVerifier를 신뢰하거나 저장하지 않는다.

### 로그인 시작 (구현 완료)

`POST /api/v1/auth/oauth/kakao/start`

- 요청 본문은 선택 사항이다. 익명 활동 연결 시 `Authorization: Bearer {anonymousAccessToken}`을 전달하거나, 본문의 `anonymousAccessTokens` 배열로 여러 익명 access token을 전달한다. 각 토큰을 검증하고 회원 중복을 제거하며, 최대 100개까지 받는다. 헤더와 본문을 함께 보내면 합쳐서 처리한다.
- 잘못된 인증 헤더를 인증 없는 요청으로 낮춰 처리하지 않는다.
- memberId, redirectUri, clientId는 요청에서 받지 않는다. 현재 웹 계약은 서버 설정의 리다이렉트만 사용한다.
- 성공: `200 OK`, `Cache-Control: no-store`.

```json
{
  "data": {
    "authorizationUrl": "https://kauth.kakao.com/oauth/authorize?...",
    "state": "서버가_발급한_무작위값",
    "loginVerifier": "시작한_클라이언트에_잠시_보관할_확인값",
    "expiresAt": "2026-10-25T12:05:00Z"
  }
}
```

인가 URL에는 response_type=code, 설정된 client_id·redirect_uri, state만 넣는다. 비밀키·서비스 토큰·loginVerifier는 URL과 로그에 싣지 않는다. 명시적 origin 허용 목록을 유지하며 서비스 refresh 쿠키를 위해 credentials를 허용한다.

### 콜백과 로그인 완료

웹 카카오 콜백은 등록된 프론트 주소에서 code·state를 받는다. code·state를 읽은 뒤 URL에서 제거하고 외부 리소스·분석 로그에 노출하지 않는다. 취소·실패 시 클라이언트의 임시 확인값을 제거하고 다시 시작한다.

`POST /api/v1/auth/oauth/kakao/login`

```json
{
  "code": "카카오_인가코드",
  "state": "콜백으로_돌아온_state",
  "loginVerifier": "로그인_시작_응답에서_보관한_확인값",
  "refreshTokenDelivery": "COOKIE"
}
```

- 웹 COOKIE 방식은 `X-CSRF-Protection: 1` 헤더와 `credentials: include`를 함께 보낸다.
- JSON 요청으로 받고 필수 값과 형식을 검증한다. 완료 시 회원 ID나 다른 익명 토큰을 받지 않는다.
- 요청 검증·소비 → 트랜잭션 밖의 제공자 HTTP 호출 → 회원 잠금·상태 재검증 및 가입/통합 → 서비스 토큰 발급 순서다.
- 소비 후 실패하거나 응답 결과를 알 수 없으면 동일 code·state를 자동 재시도하지 않는다.
- 신규 가입과 기존 로그인 모두 `200 OK`, `Cache-Control: no-store`, 기존 AuthResponse를 반환한다.

```json
{
  "data": {
    "accessToken": "우리_서비스_토큰",
    "userId": 10,
    "nickname": "민수",
    "expiresAt": "2026-10-26T12:30:00Z",
    "refreshExpiresAt": "2026-11-25T12:00:00Z"
  }
}
```

제공자 토큰은 HTTP 응답에 포함하지 않는다. 성공·실패 후 클라이언트의 임시 확인값을 삭제한다. OAuth 진행 확인값은 쿠키 공유나 HttpSession에 의존하지 않는다. COOKIE 방식 완료 응답은 서비스 refresh token을 Set-Cookie로 별도 전달한다.

### 회원 처리와 오류 응답

- 신규 소셜 계정 + 익명 회원: 기존 회원 ID·닉네임 유지, REGISTERED 전환.
- 기존 소셜 계정 + 익명 회원: 기존 회원에게 활동을 통합하고 기존 회원 닉네임을 유지한다. 익명 회원은 MERGED로 전환하고 기존 토큰과 연결 코드를 폐기한다.
- 익명 인증 없음: 기존 계정 로그인 또는 기본 닉네임 `사용자`로 신규 가입.
- 여러 익명 계정: 시작 요청에서 검증한 계정만 완료 시 통합한다. 신규 소셜 가입은 ID가 가장 작은 익명 계정을 회원으로 전환하고 나머지를 합친다. 기존 소셜 회원이면 모든 익명 계정의 활동을 해당 회원에게 이전한다. 하나라도 익명 상태가 아니면 전체 DB 변경을 거부한다.
- 계정은 공통으로 사용하고, 방별 닉네임은 `room_member.nickname`에 보존한다. 같은 방의 참여가 겹치면 대상 회원의 기존 방 닉네임을 유지한다.
- 방 생성·입장 요청은 선택적 `nickname`을 받는다. 생략하면 계정 기본 이름을 사용하며, 재입장은 기존 방 닉네임을 유지한다. 방 상세 응답의 `myNickname`으로 내 방 닉네임을 확인할 수 있다.
- 인증 응답의 `memberStatus`는 현재 계정 상태(`ANONYMOUS` 또는 `REGISTERED`)를 나타낸다.
- 이메일 자동 병합·등록 회원의 추가 로그인 수단 연결은 지원하지 않는다.
- 필수 값·형식 오류 400, 인증·state·확인값 무효/만료/소비/캐시 유실 401, 가입 직전 상태 충돌·동시 가입 충돌 409, 비활성 제공자·외부 장애 503. 로그인 시도 단계의 익명 상태 불일치는 기존대로 401이다.
- 가입 시 익명 회원의 기존 토큰·연결 코드를 모두 폐기하고 새 서비스 토큰을 발급한다. 기존 회원의 일반 로그인은 다른 기기의 세션을 유지한다.
- 동일 소셜 식별자의 동시 가입은 DB 유일성 제약으로 보호한다. 충돌한 트랜잭션은 회원 생성·상태 변경·토큰 발급을 모두 롤백하고 새 로그인 시도를 요구한다.
- 제공자 access/refresh 토큰은 V41 암호문 컬럼에 저장한다. ID 토큰은 저장하지 않는다. 탈퇴 API는 서비스 탈퇴를 먼저 커밋하고 제공자 연결 해제를 한 번 시도한다. 실패한 연결은 수동 확인 대상으로 보존한다.

### 익명 활동 통합 규칙

기존 계정 로그인 시 즉시 통합한다. `SocialMemberLoginService` → `MergeAnonymousMemberService` → `MemberActivityRepository` → 각 활동의 기존 JPA 저장소 순서로 호출한다. 통합 서비스가 순서와 대상, 회원 상태·인증 변경을 조정하고 저장소는 개별 저장 연산만 수행한다. 로그인과 통합은 같은 DB 트랜잭션을 사용한다.

방 참여·방장·업로더·좋아요는 로그인 후 활동과 권한을 이어가기 위해 통합한다. 다운로드는 진행 중 결과 조회를 유지하기 위해 이전한다. 의견은 기존 합의대로 회원 참조를 연결하되 작성 당시 닉네임·방 이름을 보존한다. 새로운 회원 참조 데이터는 자동으로 이전하지 않고 목적에 따라 대상을 명시적으로 결정한다.

| 데이터 | 처리 |
| --- | --- |
| 방 참여 | 기존 회원에게 이전. 중복 방은 한 건으로 합치고 더 이른 joined_at 유지 |
| 방장 권한 | 삭제된 방을 포함해 host_id 이전. version 증가로 오래된 방 정보의 덮어쓰기 방지 |
| 업로드 | 예약·처리 중·실패 파일을 포함해 uploader_id 이전 |
| 좋아요 | 기존 회원에게 이전. 같은 미디어의 중복은 한 건 유지 |
| 다운로드 | 진행 중인 작업을 포함해 requester_id 이전. 통합 순간 기존 작업 수가 한도를 넘으면 새 작업 생성을 제한 |
| 의견 | member_id 이전. 작성 당시 nickname·room_name 스냅샷 유지 |
| 인증 | 익명 회원은 MERGED로 유지, 모든 토큰·연결 코드 폐기. 기존 회원의 다른 세션은 유지 |

회원 ID 오름차순으로 두 회원을 잠근 뒤 상태를 재검증한다. 모든 이전·상태 변경·인증 교체는 하나의 DB 트랜잭션이며 실패하면 전부 롤백한다. 동일 익명 회원을 여러 계정에 동시에 통합하려 해도 한 요청만 성공한다.

활동 API는 기존 인증을 사용하며, 계정 통합과 활동 요청을 직렬화하기 위한 별도 회원 가드나 잠금을 추가하지 않는다.

이미 열린 SSE 연결 종료는 후속 점검 대상이다.

## 애플 API 계약

- `POST /api/v1/auth/oauth/apple/start`: 카카오와 같은 선택적 Bearer 토큰 및 `anonymousAccessTokens` 본문을 지원한다.
- `POST /api/v1/auth/oauth/apple/login`: 카카오와 동일한 `{code, state, loginVerifier}` JSON을 전달한다.
- 시작·완료 응답은 카카오와 같은 `SocialLoginStartResponse` / `AuthResponse`이며 `Cache-Control: no-store`를 사용한다. 카카오의 기존 JSON 필드와 경로는 유지한다.
- 시작 URL은 `https://appleid.apple.com/auth/authorize`로 고정하며, `response_type=code`, `response_mode=query`, 서버 설정의 client_id·redirect_uri, 서버가 생성한 state·nonce를 넣는다. loginVerifier와 비밀키는 URL에 넣지 않는다.
- 완료 요청에서 nonce·회원 ID·제공자 ID 토큰을 받지 않는다. 서버가 저장한 nonce로 코드 교환 결과의 ID 토큰을 검증한다.
- `CompleteSocialLoginService`가 제공자를 선택해 인증하고 `SocialMemberLoginService`의 가입·기존 로그인·활동 통합을 재사용한다. 다른 제공자의 state는 소비하지 않는다.
- 이름·이메일 scope를 요청하지 않는 웹 인증 코드 방식이다. 네이티브 SDK 인증 결과를 직접 받는 API는 이번 구현에 포함하지 않는다.

## 제공자 검증 및 설정

| 제공자 | 확인 절차 | 설정 |
| --- | --- | --- |
| 카카오 | code → access token → `/v2/user/me`의 양수 id | OAUTH_KAKAO_ENABLED / CLIENT_ID / CLIENT_SECRET / REDIRECT_URI |
| 애플 | code → ID 토큰 서명·issuer·audience·expiration·issued-at·nonce 검증 → sub | OAUTH_APPLE_ENABLED / CLIENT_ID / TEAM_ID / KEY_ID / PRIVATE_KEY / REDIRECT_URI |

표의 설정은 각 제공자 접두사를 붙인 환경 변수다. 기본 비활성화이며 활성화 시 필수 설정을 검증한다. 현재 웹 리다이렉트는 HTTPS만 허용한다. 제공자 콘솔·인가 요청·코드 교환에서 주소가 일치해야 한다.

애플 서명 검증 공개키는 고정된 Apple 키 주소에서 조회하고 1시간 캐시한다. 알 수 없는 kid에 대한 재조회는 1분 간격으로 제한한다. client secret은 설정된 PKCS#8 EC 개인키로 5분 유효한 ES256 JWT를 생성한다. 비밀 설정은 저장소에 커밋하지 않는다.

외부 HTTP 연결 3초·응답 5초, 자동 리다이렉트·인가 코드 자동 재시도 없음. 오류 원문·코드·토큰·비밀키는 예외와 로그에 담지 않는다.

현재 구현한 애플 웹 인가는 이름·이메일 scope 없이 response_type=code, response_mode=query다. scope를 추가하거나 인가 응답으로 ID 토큰을 직접 요청하면 응답 방식을 재검토한다. ID 토큰은 서버 코드 교환 응답에서 검증한다. 네이티브 SDK 입력 계약은 아직 지원을 확정하지 않았다.

## 제공자 토큰 암호화 저장

- V41에서 social_account에 encrypted_access_token·encrypted_refresh_token을 추가한다. 기존 행은 NULL을 허용하고 다음 정상 로그인에서 채운다. refresh만 있고 access가 없는 상태와 지원하지 않는 버전 접두사는 DB와 JPA 제약으로 제한한다.
- `SocialTokenCipher`는 AES-256-GCM, 매 암호화마다 새 12바이트 IV와 128비트 인증 태그를 사용한다. `v1:` + Base64(IV + 암호문/태그) 형식이며 제공자·subject·토큰 종류를 인증 데이터에 포함한다. GCM 매개변수는 [Java 공식 문서](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/javax/crypto/spec/GCMParameterSpec.html)를 참고한다.
- 검증된 `SocialAuthentication`의 access/refresh 토큰만 암호화한다. 새 refresh 토큰이 없으면 기존 암호문을 유지하고, 새 값이 있으면 교체한다. 토큰 만료·갱신 HTTP 호출은 아직 구현하지 않았다.
- 회원 잠금을 유지한 동일 트랜잭션에서 가입·활동 이전·토큰 저장·서비스 토큰 발급을 처리한다. 계정 메타데이터 save로 암호문이 지워지지 않도록 JPA 일반 갱신을 금지하고 전용 쿼리를 사용한다.
- 제공자 토큰·암호문·암호화 키는 API 응답과 객체 문자열 표현에 포함하지 않는다. 복호화 오류는 원문 원인을 노출하지 않는 503으로 처리한다.

### 키 설정과 기존 데이터

`OAUTH_TOKEN_ENCRYPTION_KEY`는 안전한 난수로 생성한 **32바이트를 Base64로 인코딩한 값**이다. JWT 키·제공자 client secret과 별개로 배포 환경의 비밀 설정에서 관리한다. 애플 또는 카카오를 활성화했는데 키가 없거나 형식이 틀리면 서버 시작이 실패한다. 제공자 비활성 상태에서는 키 없이 기존 익명 기능을 실행할 수 있지만 암호화·복호화는 허용하지 않는다.

운영 키를 코드·DB에 저장하거나 서버 시작마다 새로 생성하지 않는다. 현재 형식은 단일 키를 사용하므로, 기존 암호문을 이전하지 않고 환경변수만 교체하면 복호화할 수 없다. 키 보관·백업이 필요하며 키 교체 시 구키로 복호화 후 신키로 재암호화하는 별도 절차를 마련해야 한다. 이 단계에서 운영 키 생성·설정·배포는 수행하지 않았다.

V41 이전 계정처럼 보관 토큰이 없거나 제공자가 토큰 무효를 응답하면 재인증 필요로 구분한다. 자동 토큰 갱신은 이번 범위에 포함하지 않는다. 암호화 저장 완료가 탈퇴 기능 완료를 의미하지 않는다.

## 제공자 연결 해제 모듈

`UnlinkSocialAccountService.unlink(identity)`는 탈퇴 흐름에서 사용할 내부 기능이다. 저장소에서 암호문을 읽고 복호화한 후 기존 제공자 클라이언트의 unlink를 호출한다. 별도의 공개 엔드포인트는 없으며 호출자가 회원 본인 확인과 탈퇴 진행 제어를 담당해야 한다.

- 카카오: access token으로 `POST https://kapi.kakao.com/v1/user/unlink`를 호출한다. 성공 응답의 양수 정수 id가 대상 subject와 같은지 확인한다. [카카오 공식 문서](https://developers.kakao.com/docs/ko/kakaologin/rest-api#unlink)
- 애플: refresh token을 우선 선택하고 없으면 access token을 사용해 `POST https://appleid.apple.com/auth/revoke`를 호출한다. 기존 ES256 client secret 생성기를 재사용하며 token_type_hint를 함께 전달한다. 본문 없는 HTTP 200을 성공으로 처리한다. 애플 문서상 이미 무효화된 토큰도 200일 수 있다. [애플 공식 문서](https://developer.apple.com/documentation/signinwithapplerestapi/revoke-tokens)
- 보관 토큰 없음, 카카오 HTTP 401/code -401, HTTP 400/error invalid_grant는 SOCIAL_REAUTHENTICATION_REQUIRED(409)로 구분한다. invalid_client·429·5xx·리다이렉트·불명확한 응답·통신 오류·암호문 오류는 503이다. 실패 원문과 토큰은 예외에 남기지 않는다.
- 만료된 카카오 access token을 refresh token으로 자동 갱신하지 않는다. 재인증 후 저장된 토큰으로 다시 진행해야 한다. 무효 토큰이나 타임아웃만 보고 이미 연결 해제됐다고 추정하지 않는다.
- DB 트랜잭션 중 호출하면 Propagation.NEVER로 거부한다. 외부 요청의 자동 재시도는 없으며 로컬 회원·방·세션·소셜 계정·암호문은 성공/실패 모두 변경하지 않는다. 실제 외부 계정은 테스트에 사용하지 않았다.

외부 연결 해제와 로컬 DB 커밋은 하나의 트랜잭션으로 묶을 수 없다. 아래 탈퇴 API는 서비스 탈퇴를 먼저 커밋하며, 외부 성공을 확인한 경우에만 소셜 계정 행과 암호문을 제거한다. 카카오 재시도에서 토큰 무효만으로 최초 성공을 추정하지 않는다.

## DB 변경과 남은 작업

소셜 계정은 V38의 `(provider, provider_user_id)` 유일성·회원 FK·인덱스를 유지한다. 서비스 토큰 세션은 V37의 auth_session·legacy_auth_revocation을 유지하고 V42에서 전달 방식과 refresh 해시 기록을 추가한다.

로그인 시도용 JPA 엔티티·JPA 저장소는 제거했다. V39의 환경별 적용 여부를 확인할 수 없어 기존 파일은 수정하지 않고, V40에서 oauth_login_attempt만 제거한다. 이미 존재하던 진행 중 요청은 새 메모리 캐시에 복원하지 않는다. 운영 DB에 직접 명령을 실행하지 않았다.

OAuth 전용 credential CORS, 임시 HttpSession 설정 및 관련 테스트 전용 컨트롤러는 제거했다. 이번 작업 환경의 Git 제외 application-local.yml에 추가했던 Secure 예외도 제거했다.

탈퇴 API의 로컬 흐름을 연결했다. 실제 제공자 계정의 로그인·연결 해제, 웹/앱 연동·로그인 시작 요청 제한은 공개 전 검증 대상이다.

## 근거 및 검증

- [카카오 로그인 REST API](https://developers.kakao.com/docs/ko/kakaologin/rest-api)
- [애플 인가 요청](https://developer.apple.com/documentation/signinwithapplerestapi/request-an-authorization-to-the-sign-in-with-apple-server.)
- [애플 사용자 검증](https://developer.apple.com/documentation/signinwithapple/verifying-a-user?changes=_2)
- [OAuth 보안 권고 RFC 9700](https://www.rfc-editor.org/rfc/rfc9700.html)
- [Caffeine 만료·크기 제한](https://github.com/ben-manes/caffeine/wiki/Eviction)

캐시 테스트는 가상 Ticker로 만료를 진행시켜 조회/실패 요청이 만료를 연장하지 않는지, 크기 제한·재시작·동시 소비를 검증한다. 서비스 통합 테스트는 PostgreSQL로 회원 상태·DB 롤백과 무관한 소비·테이블 제거를 확인한다. 실제 카카오·애플 계정 로그인은 별도 검증이 필요하다.

메모리 캐시 전환 후 전체 테스트: 1,692개 중 1,674개 통과, 18개 건너뜀, 실패 0개.

## 탈퇴 API — 서비스 탈퇴 후 연결 해제 1회 시도

`DELETE /api/v1/auth/account`, `Authorization: Bearer {serviceAccessToken}`. 본문이나 회원 ID를 받지 않고 인증된 회원만 처리한다. 익명 회원은 409, 인증 누락·무효화된 토큰은 401이다. 성공 응답 `204 No Content`는 **쏙 서비스 탈퇴 완료**를 의미하며 제공자 연결 해제 완료를 보장하지 않는다. 응답은 한 차례의 외부 호출이 끝난 뒤 반환한다.

1. `WithdrawMemberService.withdraw`가 회원을 잠그고 REGISTERED 상태를 확인한다.
2. 같은 트랜잭션에서 미삭제 방장 방을 기존 DeleteRoomService로 논리 삭제하고, 회원을 WITHDRAWN·닉네임을 `탈퇴한 사용자`로 변경하고, 모든 인증·연결 코드를 무효화한다. 삭제된 방은 건너뛰며 기존 방 삭제 이벤트를 재사용한다. 다른 사람의 방과 그 방에 올린 파일은 유지한다.
3. 소셜 계정 행과 암호화 토큰은 그대로 둔다. **WITHDRAWN 회원에 연결된 social_account가 미정리 기록**이므로 별도 작업 테이블이나 스케줄러가 없다. 탈퇴 커밋 직후 프로세스가 중단돼도 이 기록은 남는다.
4. `WithdrawAccountService`가 트랜잭션 밖에서 제공자 연결 해제를 한 번 호출한다. 성공하면 별도 트랜잭션으로 원래 소셜 계정 행 ID·회원 ID를 확인해 행과 암호문을 삭제한다.
5. 외부 오류·토큰 무효·응답 유실·후속 DB 실패는 탈퇴를 되돌리지 않는다. 회원 ID와 소셜 계정 행 ID만 경고 로그에 남기고 수동 확인한다. 자동 재시도·토큰 자동 갱신·대기 작업 스케줄러는 없다.

미정리 소셜 계정은 유일성 제약과 WITHDRAWN 회원 상태 검사로 같은 소셜 계정의 가입·로그인·익명 활동 통합을 거부한다(현재 SOCIAL_AUTH_CONFLICT, 409). 정리 후 재가입은 새로운 회원 ID로 생성되며 이전 활동과 권한은 복구하지 않는다. 탈퇴한 토큰으로 API를 재요청하면 401이며 외부 호출을 재실행하지 않는다.

### 미정리 연결 수동 확인

다음 읽기 전용 쿼리로 대상을 찾는다. 토큰·암호문·제공자 사용자 식별자는 출력하지 않는다.

```sql
SELECT a.id AS social_account_id, a.member_id, a.provider
FROM social_account a
JOIN member m ON m.id = a.member_id
WHERE m.status = 'WITHDRAWN'
ORDER BY a.id;
```

- 행이 남았다는 사실만으로 외부 실패라고 단정하지 않는다. 외부 성공 후 응답 유실·DB 정리 실패일 수도 있다.
- 자동 또는 일괄 재호출하지 않는다. 로그와 제공자 측 확인 가능한 자료로 연결 상태를 확인한다. 확인 불가능하면 대기 상태와 재가입 보류를 유지한다.
- 연결 해제 성공이 확인되면 해당 기존 계정 행만 정리한다. 내부 `WithdrawMemberService.completeUnlink`는 회원의 WITHDRAWN 상태와 원래 행 ID·회원 ID를 확인한다. 별도 공개 재처리 API는 제공하지 않는다.
- 토큰 만료·누락으로 해제할 수 없다면 제공자별 수동 연결 해제 안내와 확인이 필요하다. 자동 재인증 복구 흐름은 이번 범위에 없다.
- 미정리 기록은 자동 만료·삭제하지 않는다. 운영자가 위 조회로 확인해야 하며, 키는 미정리 암호문을 읽을 수 있도록 유지한다.

활동 API 전체를 직렬화하는 가드는 추가하지 않는다. 탈퇴 전에 인증을 통과한 진행 중 요청과 이미 열린 SSE의 종료 정책은 기존과 마찬가지로 별도 검증 대상이다.


## 서비스 refresh token (V42)

카카오·애플 제공자 refresh token과 별개다. 익명 생성, 연결 코드 로그인, 소셜 가입·로그인 모두 같은 정책을 적용한다.

- Access token 기본 TTL은 30분(`JWT_ACCESS_TOKEN_TTL=30m`), refresh 및 세션 만료는 최초 발급부터 30일이다. 로컬/배포 설정에 남아 있는 30d override도 변경해야 한다.
- 갱신할 때 두 토큰을 발급하지만 최초 세션 만료 시각은 연장하지 않는다. access 만료도 세션 만료를 넘지 않는다.
- `auth_session.expires_at`은 새 세션에서 30일의 절대 만료다. JWT에는 개별 access 만료를 넣는다. 세션 ID와 전달 방식은 갱신 중 유지한다.
- refresh는 32바이트 암호학적 난수의 base64url 표현이다. DB에는 SHA-256 해시·세션 ID·사용 여부만 저장한다. 사용된 해시는 재사용 감지를 위해 남긴다. 별도 청소 스케줄러는 추가하지 않는다.
- 사용한 refresh를 다시 보내면 해당 세션을 폐기하고 401을 반환한다. 다른 기기 세션은 유지한다. 세션 폐기는 오류 응답에도 커밋한다.
- 같은 토큰으로 동시에 갱신하면 하나만 성공하고 뒤의 요청이 재사용으로 감지되어 세션이 폐기된다. 클라이언트는 탭 간에도 갱신을 직렬화해야 하며, 응답 유실 시 이전 토큰을 무조건 재시도하지 않는다.
- 로그아웃·익명 통합·탈퇴는 기존 세션 폐기 경로를 재사용해 access와 refresh를 함께 차단한다. refresh 만료·소비 기록을 지워서 이전 토큰을 다시 허용하지 않는다.
- 기존에 발급된 access token은 원래 만료/폐기 정책을 유지하며 refresh를 소급 발급하지 않는다. 새 로그인 또는 연결 코드 로그인부터 발급한다.

### 전달 계약

발급 요청(anonymous, link, oauth/*/login)에 `refreshTokenDelivery`를 전달한다. 생략 시 COOKIE이며, 정의되지 않은 값은 400이다. 이 값은 클라이언트의 신원을 증명하지 않는다.

| 모드 | 발급/갱신 응답 | 갱신 요청 |
|---|---|---|
| COOKIE (웹) | access는 JSON, refresh는 `sssok_refresh` 쿠키, JSON에 refresh 원문 없음 | 쿠키 + `X-CSRF-Protection: 1` |
| BODY (네이티브 앱) | access·refresh 모두 JSON, refreshExpiresAt 포함 | JSON `{ "refreshToken": "..." }` |

공통 경로: `POST /api/v1/auth/refresh`. access token은 필요 없다. 쿠키와 본문 refresh를 동시에 보내면 400, 없는/만료된/폐기된 토큰이나 저장된 전달 방식과 다르면 401이다. 반환하는 userId·nickname은 현재 회원 정보이며, 모든 토큰 응답은 `Cache-Control: no-store`다.

쿠키는 HttpOnly, Secure, SameSite=Lax, Path=/api/v1/auth, Domain 미지정이다. Max-Age는 최초 30일 만료까지의 남은 시간이다. 로그아웃·탈퇴 성공 응답은 같은 경로로 Max-Age=0을 설정한다. 일반 API 인증은 계속 Authorization Bearer를 사용한다.

COOKIE 방식 발급·갱신은 `X-CSRF-Protection: 1`이 필요하다. Origin이 있으면 CORS 허용 목록과 일치해야 한다. CORS는 명시적 출처에 credentials를 허용한다. 웹은 발급·갱신·로그아웃·탈퇴 호출 시 `credentials: "include"`를 설정한다. 앱은 refresh를 OS 보안 저장소에 보관하고 BODY 갱신 요청에 브라우저 쿠키를 섞지 않는다.

로컬 HTTP에서는 `auth.cookie.secure=false` 설정이 필요할 수 있다. 운영은 true를 유지한다. 실제 프런트/API가 서로 다른 사이트라면 `auth.cookie.same-site=None`과 Secure가 필요하며, 브라우저의 서드파티 쿠키 제한도 확인해야 한다. 현재 기본값은 같은 사이트 배포를 전제로 한다.

### 프런트 연동 전에 해결할 점

현재 프런트는 `roomSessionStorage.ts`에서 방마다 별도 익명 계정을 생성·저장한다. 이번 COOKIE 계약은 브라우저당 현재 계정 하나를 전제로 하므로, 여러 방의 서로 다른 익명 계정에 그대로 붙이면 마지막 로그인 쿠키가 앞선 계정 쿠키를 덮는다. 프런트 계정 세션을 공통화할지, 방별 독립 계정을 유지할지 결정한 뒤 연동해야 한다. 이 작업에서는 프런트 코드를 수정하지 않았다.

30분 access 만료 전에 자동 갱신 연동, 갱신 직렬화, 쿠키 credentials/CSRF 헤더 적용이 필요하다. 이 연동 없이 현재 프런트와 함께 배포하면 익명 발급 요청이 403으로 거부되고 갱신도 동작하지 않는다. 익명 사용자가 세션 만료 후 같은 계정을 복구하려면 만료 전 소셜 연결 등 복구 수단이 필요하다.
