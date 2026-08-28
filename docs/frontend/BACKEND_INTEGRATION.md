# 백엔드 API 연동

프론트가 실제 서버에 붙는 방법을 정리한다.

## 서버 주소

| | 주소 |
| --- | --- |
| API | `https://api.ssssok.com/api/v1` |
| Swagger UI | https://api.ssssok.com/swagger-ui/index.html |
| OpenAPI | https://api.ssssok.com/v3/api-docs |

`API_BASE_URL` 의 기본값이 이 주소라 따로 설정할 게 없다.

주의할 점 둘.

- **`http://43.201.47.241:8080` 은 쓰지 않는다.** 외부에서 연결이 되지 않고, 설령 열려도
  배포된 프론트는 https 라 `http://` 주소는 브라우저가 혼합 콘텐츠로 막는다.
- **`/health` 는 `/api/v1` 밖에 있다.** 배포 헬스체크가 그 경로를 그대로 참조해서
  접두사에서 뺐다 ([WebConfig.java](../../backend/src/main/java/com/sssok/infrastructure/config/WebConfig.java)).

CORS 는 서버가 이미 `http://localhost:3000` 을 허용한다. 개발 서버에서 바로 붙는다.

## 실행 모드

목을 어디까지 씌울지는 `MOCK` 환경변수가 정한다 ([mock.ts](../../frontend/src/shared/config/mock.ts)).

| 모드 | 실행 | 동작 |
| --- | --- | --- |
| `off` (기본) | `pnpm start` | 목 없이 모든 요청이 실서버로 간다 |
| `full` | `pnpm start:mock` | 모든 요청을 목이 답한다. 서버가 죽었거나 오프라인일 때 |

한때 실서버에 없던 미디어 목록 조회 한 경로만 목이 메우는 `hybrid` 모드가 있었다.
백엔드에 목록 API 가 생기면서([#107](https://github.com/woowacourse-teams/2026-sssOK/issues/107))
역할이 끝나 함께 지웠다.

배포 빌드는 `MOCK` 과 무관하게 목을 띄우지 않는다. `index.tsx` 가 `NODE_ENV` 로 먼저 끊고,
그 비교가 빌드 시점에 접혀서 목 번들 자체가 결과물에 딸려가지 않는다.

로컬 백엔드를 볼 때:

```bash
API_BASE_URL=http://localhost:8080/api/v1 pnpm start
```

## 붙은 것

프론트가 부르는 엔드포인트는 모두 실서버에 있다. 마지막 구멍이던 미디어 목록 조회도
[#107](https://github.com/woowacourse-teams/2026-sssOK/issues/107) 로 생겼다
([MediaQueryController](../../backend/src/main/java/com/sssok/presentation/api/media/MediaQueryController.java)).

알아둘 것:

- **`thumbnailUrl`·`width`·`duration` 은 워커가 채우기 전까지 `null` 이다.**
  목록 응답의 파생값은 서버 워커가 만든다 — 없는 동안 프론트가 자리표시로 버텨야 한다.
- **`thumbnailUrl`/`originalUrl` 은 상대 경로다** (`/rooms/{roomId}/media/{mediaId}/thumbnail`).
  `<img src>` 에 넣기 전에 `mediaAssetUrl` 로 절대 주소로 풀어야 한다
  ([mediaAssetUrl.ts](../../frontend/src/entities/media/lib/mediaAssetUrl.ts)).
- **`photoCount` 배지는 업로드 뒤 갱신되지 않는다.** 방 조회를 다시 부르지 않아
  페이지를 열었을 때 값에 머문다.

## 막혀 있는 것 — R2 버킷 CORS

**브라우저에서는 업로드가 안 된다.** 서명 URL 은 정상 발급되지만, 브라우저가 PUT 전에
보내는 프리플라이트를 R2 가 거절한다.

```
OPTIONS <presigned-url>
  Origin: http://localhost:3000
  Access-Control-Request-Method: PUT
→ 403 Forbidden
   <Code>Unauthorized</Code>
   <Message>CORS not configured for this bucket</Message>
```

서버 쪽 파이프라인 자체는 멀쩡하다. curl 은 CORS 를 따지지 않으므로 그대로 통과한다 —
발급 200 → R2 PUT 200 → 완료 등록 201 까지 확인했다. 순수하게 **버킷 설정 문제**이고,
프론트에서 고칠 수 있는 게 없다.

`sssok-dev` 버킷에 CORS 규칙이 필요하다. 최소한 이 정도:

- `AllowedOrigins`: 개발 `http://localhost:3000`, 배포 프론트 도메인
- `AllowedMethods`: `PUT`
- `AllowedHeaders`: `content-type` (서명 대상이라 반드시 포함)
- `ExposeHeaders`: `ETag`

이게 뚫리기 전까지 업로드를 손으로 확인하려면 `pnpm start:mock` 을 쓴다.

## 요청·응답 규약

- 성공 응답은 항상 `{ "data": ... }` 로 한 겹 감싸여 온다. `apiClient` 가 벗겨서 돌려준다.
- 실패 응답은 `{ "code": ..., "message": ... }` 다. `apiClient` 가 `ApiError` 로 바꿔 던진다.
- 조회만 방 코드(문자열)를 쓰고, 나머지는 조회 응답의 `roomId`(숫자)를 쓴다.
- **입장을 먼저 해야 업로드가 열린다.** `/rooms/*/media/**` 에 방 참여 검증이 걸려 있어서
  `POST /rooms/{roomId}/members` 없이 업로드하면 403 이 난다.
  방을 만든 사람은 생성 시점에 참여자로 등록되므로(`joined=true`) 따로 부르지 않아도 된다.
