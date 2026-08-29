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

- **파생값은 등록 직후 잠깐 `null` 이다.** 등록 응답은 `status: PROCESSING` 에
  `thumbnailUrl`·`width`·`height` 가 비어 있고, 워커가 처리를 마치면 `READY` 가 되면서 채워진다.
  실측으로 2초 안쪽이었다. 목록에는 `READY` 만 내려오므로 갤러리는 신경 쓰지 않아도 된다.
- **`thumbnailUrl`/`originalUrl` 은 상대 경로다** (`/rooms/{roomId}/media/{mediaId}/thumbnail`).
  `<img src>` 에 넣기 전에 `mediaAssetUrl` 로 절대 주소로 풀어야 한다
  ([mediaAssetUrl.ts](../../frontend/src/entities/media/lib/mediaAssetUrl.ts)).
- **`photoCount` 배지는 업로드 뒤 갱신되지 않는다.** 방 조회를 다시 부르지 않아
  페이지를 열었을 때 값에 머문다.

## R2 버킷 CORS

브라우저는 스토리지에 직접 PUT 하고(업로드) 직접 GET 한다(다운로드). 둘 다 크로스 오리진이라
버킷에 CORS 규칙이 있어야 한다. **지금은 설정돼 있다** — 규칙이 없던 동안에는
브라우저 업로드·다운로드가 통째로 막혀 있었다.

확인:

```bash
bash frontend/scripts/check-r2-cors.sh
```

```
HTTP/1.1 204 No Content
Access-Control-Allow-Origin: http://localhost:3000
Access-Control-Allow-Methods: PUT, GET, HEAD
Access-Control-Allow-Headers: content-type
```

- `PUT` 은 업로드, `GET` 은 다운로드에 쓴다. 다운로드는 `<a download>` 가 크로스 오리진에서
  안 먹어 프론트가 바이트를 직접 받아야 해서 GET 도 필요하다.
- `content-type` 은 서명 대상이라 `AllowedHeaders` 에 반드시 있어야 한다. 빠지면 403 이다.
- 배포 도메인을 추가할 때도 같은 스크립트로 확인할 수 있다 (`check-r2-cors.sh <origin>`).

**증상 구분** — 버킷 CORS 가 빠지면 브라우저에서만 실패하고 서버는 멀쩡해 보인다.
curl 은 CORS 를 따지지 않아 그대로 통과하기 때문이다. 콘솔에 `ERR_FAILED` 만 뜨고
스토리지 쪽에는 시도 흔적조차 안 남으면 이걸 의심한다.

## 요청·응답 규약

- 성공 응답은 항상 `{ "data": ... }` 로 한 겹 감싸여 온다. `apiClient` 가 벗겨서 돌려준다.
- 실패 응답은 `{ "code": ..., "message": ... }` 다. `apiClient` 가 `ApiError` 로 바꿔 던진다.
- 조회만 방 코드(문자열)를 쓰고, 나머지는 조회 응답의 `roomId`(숫자)를 쓴다.
- **입장을 먼저 해야 업로드가 열린다.** `/rooms/*/media/**` 에 방 참여 검증이 걸려 있어서
  `POST /rooms/{roomId}/members` 없이 업로드하면 403 이 난다.
  방을 만든 사람은 생성 시점에 참여자로 등록되므로(`joined=true`) 따로 부르지 않아도 된다.
