# 다른 사용자 자원 접근 거부 확인 기록 (#604)

#600(카카오·애플 회원 인증과 익명 활동 통합) 머지 후, 다른 사용자의 자원에 접근하는 요청을 **dev 서버에 직접 보내** 거부되는지 확인한 기록이다.
테스트 코드 통과가 아니라 실제 요청·응답을 남기는 것이 목적이다.

## 한눈에 보기

| 항목 | 결과 |
| --- | --- |
| 실행 일시 | 2026-10-28 10:30 ~ 10:36 KST (카카오 확인 13:21 ~ 13:22 KST) |
| 대상 | `https://dev-api.ssssok.com/api/v1` (release 1.10.0, backend 6.0.0, `865a1be0`) |
| 기록한 요청 | 71건 (준비 7, A 상태 조회 6, 검증 58). R2 업로드와 연결 코드 발급 등 보조 요청은 표에서 뺐다 |
| B → A 방 자원 요청 | **29건 중 29건 거부** (403 26건, 401 2건, 400 1건) |
| 거부 응답에 A 데이터 포함 | **0건** |
| 거부 후 A의 방·미디어·폴더 변경 | **0건** (요청 전후 A 토큰 조회 결과 동일) |
| 인증 세션 경계 | refresh 재사용 5건·연결 코드 4건·로그아웃 3건·CSRF 3건 모두 의도대로 거부 |
| 소셜 회원 경계 (실제 카카오 로그인) | 통합된 익명 계정의 기존 토큰 3건, 탈퇴 후 기존 토큰 4건 모두 401 |
| **거부되지 않은 요청** | **1건** — 공유 코드 없이 roomId만으로 남의 방에 입장 가능 (아래 [발견 사항](#발견-사항), [#626](https://github.com/woowacourse-teams/2026-sssOK/issues/626)) |

원본 기록: [evidence/604-access-denial/](evidence/604-access-denial/)

- `run.sh` — 실행한 스크립트 그대로 (다시 돌리면 같은 순서로 재현된다)
- `results.tsv` — 요청마다 메서드·경로·토큰 주인·기대·실제 상태 코드·응답 본문 앞 160자
- `run-kakao.sh`, `results-kakao.tsv` — 실제 카카오 로그인으로 진행한 소셜 회원 경계 확인
- `run-output.txt` — 실행 출력
- `snapshot-before.json`, `snapshot-after.json` — B 요청 전후 A 토큰으로 조회한 방·미디어·폴더 상태

토큰 원문은 기록에 없다. 응답의 JWT는 `<JWT>`, refresh token은 `<REFRESH>`, 서명 URL의 서명값은 `<redacted>`로 바꿔 저장했다.

## 준비

| 사용자 | 만든 방법 | userId | 역할 |
| --- | --- | --- | --- |
| A | `POST /auth/anonymous` | 903 | 자원 주인. 방 176(`604A비밀방`), 폴더 43(`604A폴더`), 사진 7774(`604-A-secret.jpg`, 폴더 43에 담음, 좋아요 1), ZIP 작업 7 생성 |
| A2 | A가 발급한 연결 코드로 `POST /auth/link` | 903 | A의 두 번째 기기 세션. 로그아웃 확인용 |
| B | `POST /auth/anonymous` | 904 | 공격자. A의 방에 참여하지 않음. 본인 방 177 생성 |
| C | `POST /auth/anonymous` | 905 | refresh token 재사용 확인용 |
| D | `POST /auth/anonymous` (쿠키 방식) | 906 | CSRF 헤더 확인용 |

사진은 실제 업로드 흐름(업로드 URL 발급 → R2 PUT 200 → 완료 등록 201)으로 올렸다.
소셜 회원 확인은 3절에서 실제 카카오 계정으로 따로 진행했다.

## 1. B 토큰으로 A의 방 자원 요청

경로는 모두 `/api/v1` 아래다. B는 방 176에 참여하지 않은 상태다.

| ID | 메서드 | 경로 | 토큰 | 기대 | 실제 | 응답 본문 요약 |
| --- | --- | --- | --- | --- | --- | --- |
| T01 | GET | `/rooms/176` | B | 400 | 400 | `INVALID_ROOM_CODE` — 방 조회는 roomId가 아닌 공유 코드로만 된다 |
| T02 | PATCH | `/rooms/176` | B | 403 | 403 | `NOT_ROOM_HOST` |
| T27 | DELETE | `/rooms/176` | B | 403 | 403 | `NOT_ROOM_HOST` |
| T03 | GET | `/rooms/176/events` (SSE) | B | 403 | 403 | `ROOM_MEMBERSHIP_REQUIRED` |
| T04 | GET | `/rooms/176/notification-setting` | B | 403 | 403 | `NOT_ROOM_MEMBER` |
| T05 | PUT | `/rooms/176/notification-setting` | B | 403 | 403 | `NOT_ROOM_MEMBER` |
| T06 | POST | `/rooms/176/upload-requests` | B | 403 | 403 | `NOT_ROOM_MEMBER` |
| T07 | GET | `/rooms/176/media` | B | 403 | 403 | `NOT_ROOM_MEMBER` |
| T08 | GET | `/rooms/176/media/all` | B | 403 | 403 | `NOT_ROOM_MEMBER` |
| T09 | GET | `/rooms/176/media/7774` | B | 403 | 403 | `NOT_ROOM_MEMBER` |
| T10 | PUT | `/rooms/176/media/7774/likes` | B | 403 | 403 | `NOT_ROOM_MEMBER` |
| T11 | DELETE | `/rooms/176/media/7774/likes` | B | 403 | 403 | `NOT_ROOM_MEMBER` |
| T12 | POST | `/rooms/176/media/upload-urls` | B | 403 | 403 | `NOT_ROOM_MEMBER` |
| T13 | POST | `/rooms/176/media/7774/upload-url` | B | 403 | 403 | `NOT_ROOM_MEMBER` |
| T14 | POST | `/rooms/176/media` (완료 등록) | B | 403 | 403 | `NOT_ROOM_MEMBER` |
| T25 | DELETE | `/rooms/176/media` (일괄) | B | 403 | 403 | `NOT_ROOM_MEMBER` |
| T26 | DELETE | `/rooms/176/media/7774` | B | 403 | 403 | `NOT_ROOM_MEMBER` |
| T20 | POST | `/rooms/176/folders` | B | 403 | 403 | `NOT_ROOM_MEMBER` |
| T21 | PATCH | `/rooms/176/folders/43` | B | 403 | 403 | `NOT_ROOM_MEMBER` |
| T24 | DELETE | `/rooms/176/folders/43` | B | 403 | 403 | `NOT_ROOM_MEMBER` |
| T22 | PUT | `/rooms/176/media/folders` (폴더에 담기) | B | 403 | 403 | `NOT_ROOM_MEMBER` |
| T23 | DELETE | `/rooms/176/media/folders` (폴더에서 꺼내기) | B | 403 | 403 | `NOT_ROOM_MEMBER` |
| T15 | GET | `/rooms/176/downloads/media/7774` (단건) | B | 403 | 403 | `NOT_ROOM_MEMBER`, `Location` 헤더 없음 |
| T16 | POST | `/rooms/176/downloads/batch` | B | 403 | 403 | `NOT_ROOM_MEMBER` |
| T17 | POST | `/rooms/176/downloads/zip` | B | 403 | 403 | `NOT_ROOM_MEMBER` |
| T18 | GET | `/rooms/176/downloads/zip/7` (A의 작업) | B | 403 | 403 | `NOT_ROOM_MEMBER` |
| T19 | GET | `/rooms/177/downloads/zip/7` (B 본인 방 경로로 A의 작업) | B | 403 | 403 | `DOWNLOAD_FORBIDDEN` — 작업 요청자가 아니면 거부 |
| T29 | GET | `/rooms/176/media/all` | 없음 | 401 | 401 | `UNAUTHORIZED` |
| T30 | GET | `/rooms/176/media/all` | 위조(A 토큰 마지막 글자 변경) | 401 | 401 | `UNAUTHORIZED` |

참고로 T28 `GET /members/me/rooms`(B)는 200이고, 목록에는 B 본인 방 177만 있다.

- 응답 본문 노출 검사: T01~T30의 응답에서 A의 방 이름·방 코드·폴더 이름·파일명을 찾았고, 검출 0건이다.

### 거부 후 A 데이터 확인

B의 요청 전후에 A 토큰으로 `GET /rooms/{code}`, `GET /rooms/176/media/all`, `GET /rooms/176/media/7774`를 조회해 비교했다.
비교 항목은 방 이름·상태·방장·업로드 권한·사진 수·폴더 목록, 미디어 ID·파일명·폴더 소속·좋아요 수다.

```text
$ diff snapshot-before.json snapshot-after.json
(차이 없음)
```

B가 시도한 수정·삭제 요청(방 이름 변경, 폴더 생성·이름 변경·삭제, 폴더 담기·꺼내기, 좋아요, 미디어 삭제, 방 삭제)이 하나도 반영되지 않았다.

## 2. 인증 세션 경계

### Refresh token 재사용 (C)

| ID | 메서드 | 경로 | 토큰 | 기대 | 실제 | 응답 본문 요약 |
| --- | --- | --- | --- | --- | --- | --- |
| U01 | POST | `/auth/refresh` | C의 refresh R1 | 200 | 200 | 새 토큰 AT2·R2 발급 |
| U02 | POST | `/auth/refresh` | R1 재사용 (5초 이내) | 401 | 401 | `UNAUTHORIZED` |
| U03 | GET | `/members/me/rooms` | AT2 | 200 | 200 | 유예 구간이라 세션 유지 |
| U04 | POST | `/auth/refresh` | R1 재사용 (6초 뒤) | 401 | 401 | `UNAUTHORIZED` |
| U05 | POST | `/auth/refresh` | R2 | 401 | 401 | 세션이 폐기돼 최신 refresh도 거부 |
| U06 | GET | `/members/me/rooms` | AT2 | 401 | 401 | 세션이 폐기돼 access token도 거부 |

5초가 지난 뒤 R1을 다시 쓰면 401이 나고, 같은 세션의 R2·AT2까지 거부된다. 세션 폐기를 실제로 확인했다.

### 연결 코드 (B가 A 계정으로 로그인 시도)

| ID | 메서드 | 경로 | 요청 | 기대 | 실제 | 응답 본문 요약 |
| --- | --- | --- | --- | --- | --- | --- |
| L01 | POST | `/auth/link` | 코드 없음 | 400 | 400 | `INVALID_LINK_CODE` |
| L02 | POST | `/auth/link` | 발급된 적 없는 코드 `000000` | 404 | 404 | `LINK_CODE_NOT_FOUND` |
| L03 | POST | `/auth/link` | A2가 이미 사용한 코드 | 404 | 404 | `LINK_CODE_NOT_FOUND` |
| L04 | POST | `/auth/link` | A가 발급하고 5분 10초 지난 코드 | 410 | 410 | `LINK_CODE_EXPIRED` |

### 로그아웃 (A의 두 번째 기기 세션 A2)

| ID | 메서드 | 경로 | 토큰 | 기대 | 실제 | 응답 본문 요약 |
| --- | --- | --- | --- | --- | --- | --- |
| O01 | POST | `/auth/logout` | A2 access | 204 | 204 | — |
| O02 | GET | `/members/me/rooms` | A2 access (로그아웃 후) | 401 | 401 | `UNAUTHORIZED` |
| O03 | POST | `/auth/refresh` | A2 refresh (로그아웃 후) | 401 | 401 | `UNAUTHORIZED` |
| O04 | GET | `/members/me/rooms` | A access | 200 | 200 | 다른 기기 세션은 유지 |

### 쿠키 방식 CSRF 헤더 (D)

| ID | 메서드 | 경로 | 요청 | 기대 | 실제 | 응답 본문 요약 |
| --- | --- | --- | --- | --- | --- | --- |
| X01 | POST | `/auth/anonymous` | `COOKIE` 방식, `X-CSRF-Protection` 없음 | 4xx | 403 | `INVALID_COOKIE_AUTH_REQUEST` |
| X02 | POST | `/auth/anonymous` | `COOKIE` 방식, 헤더 포함 | 201 | 201 | `Set-Cookie: sssok_refresh=<REFRESH>; Path=/api/v1/auth; Secure; HttpOnly; SameSite=Lax` |
| X03 | POST | `/auth/refresh` | 쿠키만, 헤더 없음 | 4xx | 403 | `INVALID_COOKIE_AUTH_REQUEST` |
| X04 | POST | `/auth/refresh` | 쿠키 + 헤더, `Origin: https://evil.example.com` | 4xx | 403 | `Invalid CORS request` |
| X05 | POST | `/auth/refresh` | 쿠키 + 헤더 | 200 | 200 | 정상 갱신 (대조군) |

### 익명 계정은 탈퇴할 수 없다

탈퇴는 소셜 가입 회원만 할 수 있다. 익명 계정 A의 `DELETE /auth/account`는 409 `SOCIAL_AUTH_CONFLICT`가 났고(W01), 탈퇴가 되지 않았으니 이후 토큰도 그대로 유효했다(W02 200, W03 200).
그래서 탈퇴 확인은 실제 카카오 회원으로 따로 진행했다(아래 3절).

## 3. 소셜 회원 경계 (실제 카카오 로그인)

2026-10-28 13:21 ~ 13:22 KST에 실제 카카오 계정으로 진행했다. 스크립트는 [run-kakao.sh](evidence/604-access-denial/run-kakao.sh), 결과는 [results-kakao.tsv](evidence/604-access-denial/results-kakao.tsv)에 있다.
카카오 로그인과 인가 코드 입력은 계정 주인이 브라우저와 터미널에서 직접 했고, 토큰·인가 코드·`state`·`loginVerifier` 원문은 기록하지 않았다.

진행 순서:

1. 익명 계정 X(913)를 만들고 방 183(`604X익명방`)을 만든다.
2. X 토큰을 `Authorization` 헤더에 넣고 `POST /auth/oauth/kakao/start`를 호출한다.
3. 카카오 로그인 후 받은 인가 코드로 `POST /auth/oauth/kakao/login`을 호출한다. 기존 카카오 회원 M(908, `REGISTERED`)으로 로그인되며 X의 활동이 M으로 통합된다.
4. X의 기존 토큰으로 요청한다.
5. M이 탈퇴한 뒤 M과 X의 기존 토큰으로 요청한다.

### 통합된 익명 계정의 기존 토큰

| ID | 메서드 | 경로 | 토큰 | 기대 | 실제 | 응답 본문 요약 |
| --- | --- | --- | --- | --- | --- | --- |
| K04 | POST | `/auth/oauth/kakao/login` | — | 200 | 200 | 회원 M(908) 로그인, `memberStatus: REGISTERED` |
| M01 | GET | `/members/me/rooms` | X access | 401 | 401 | `UNAUTHORIZED` |
| M02 | GET | `/rooms/183/media/all` | X access | 401 | 401 | `UNAUTHORIZED` — X가 만든 방도 X 토큰으로는 접근 불가 |
| M03 | POST | `/auth/refresh` | X refresh | 401 | 401 | `UNAUTHORIZED` |
| M04 | GET | `/members/me/rooms` | M access | 200 | 200 | 방 183(`604X익명방`, `isHost: true`)이 M의 목록에 있다 — 통합 확인 |

### 탈퇴 후 기존 토큰

| ID | 메서드 | 경로 | 토큰 | 기대 | 실제 | 응답 본문 요약 |
| --- | --- | --- | --- | --- | --- | --- |
| W01 | DELETE | `/auth/account` | M access | 204 | 204 | 탈퇴 완료 |
| W02 | GET | `/members/me/rooms` | M access (탈퇴 후) | 401 | 401 | `UNAUTHORIZED` |
| W03 | POST | `/auth/refresh` | M refresh (탈퇴 후) | 401 | 401 | `UNAUTHORIZED` |
| W04 | GET | `/rooms/183/media/all` | M access (탈퇴 후) | 401 | 401 | `UNAUTHORIZED` |
| W05 | GET | `/members/me/rooms` | X access (탈퇴 후) | 401 | 401 | `UNAUTHORIZED` |

## 발견 사항

### 공유 코드 없이 roomId만으로 남의 방에 입장할 수 있다

| ID | 메서드 | 경로 | 토큰 | 기대 | 실제 | 응답 본문 요약 |
| --- | --- | --- | --- | --- | --- | --- |
| T31 | POST | `/rooms/176/members` | B | 403 | **201** | `{"roomId":176,"userId":904,"displayName":"604B","hostId":903,...}` |
| T32 | GET | `/rooms/176/media/all` | B | 403 | **200** | A의 사진 `604-A-secret.jpg`, 업로더 `604A`, 폴더 43 소속이 그대로 노출 |
| T33 | DELETE | `/rooms/176/members/me` | B | 204 | 204 | 확인 후 B를 방에서 내보냄 |

- 방에 들어가는 비밀은 공유 링크의 방 코드(`ZKY6UMDH` 같은 8자리)여야 한다. 그런데 입장 API는 roomId만 받고 코드를 확인하지 않는다(`RoomMembershipService.join`).
- roomId는 순차 증가하는 숫자다(이번에 만든 방이 176, 177). 링크를 받지 않은 사람도 숫자를 하나씩 올려 가며 모든 방에 입장할 수 있다.
- 입장하면 1절에서 거부된 조회·다운로드·폴더 수정·삭제가 전부 열린다. 1절의 403은 "참여자가 아니면 거부"까지만 보장하고, "참여자가 되는 것"은 막지 못한다.
- 별도 bug 이슈로 등록했다: [#626 초대 코드 없이 방 번호만으로 방 입장 가능](https://github.com/woowacourse-teams/2026-sssOK/issues/626)

## 정리한 데이터

- 방 176(A), 177(B)은 `DELETE /rooms/{roomId}`로 삭제했다(Z01·Z02 200).
- 카카오 확인에 쓴 회원 M(908)은 탈퇴했고, 방장이던 방 183도 탈퇴와 함께 삭제됐다(`GET /rooms/{code}` 조회 시 `status: DELETED`). M이 사용한 카카오 계정과 dev 앱의 연결도 해제됐다.
