# 백엔드 API 연동

프론트가 실제 서버에 붙는 방법과, 아직 목으로 메워둔 구멍을 정리한다.

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
| `hybrid` (기본) | `pnpm start` | 실서버에 붙되 **미디어 목록 조회만** 목이 답한다 |
| `full` | `pnpm start:mock` | 모든 요청을 목이 답한다. 서버가 죽었거나 오프라인일 때 |
| `off` | `MOCK=off pnpm start` | 목을 아예 띄우지 않는다 |

배포 빌드는 `MOCK` 과 무관하게 목을 띄우지 않는다. `index.tsx` 가 `NODE_ENV` 로 먼저 끊고,
그 비교가 빌드 시점에 접혀서 목 번들 자체가 결과물에 딸려가지 않는다.

로컬 백엔드를 볼 때:

```bash
API_BASE_URL=http://localhost:8080/api/v1 MOCK=off pnpm start
```

## 붙은 것과 안 붙은 것

프론트가 부르는 엔드포인트 열 개 중 아홉이 실서버에 있다.

| 엔드포인트 | 서버 |
| --- | --- |
| `POST /auth/anonymous` | 있음 |
| `POST /rooms` | 있음 |
| `GET /rooms/{code}` | 있음 |
| `POST /rooms/{roomId}/members` | 있음 |
| `POST /rooms/{roomId}/media/upload-urls` | 있음 |
| `POST /rooms/{roomId}/media` | 있음 |
| `POST /rooms/{roomId}/media/{mediaId}/upload-url` | 있음 |
| `POST /rooms/{roomId}/downloads` | 있음 |
| `GET /rooms/{roomId}/downloads/{jobId}` | 있음 |
| **`GET /rooms/{roomId}/media`** | **없음** |

### 하나 남은 구멍 — 미디어 목록 조회

백엔드에 목록 조회 API 가 없다. 갤러리가 통째로 그 위에 서 있어서, 목이 없으면
방에 들어가자마자 화면이 깨진다. 그래서 `hybrid` 모드가 그 한 경로만 목으로 잡는다
([hybrid.ts](../../frontend/src/mocks/handlers/hybrid.ts)).

목록을 채우는 방법이 조금 특이하다. 완료 등록(`POST /rooms/{roomId}/media`)은
**진짜 서버가 처리하고**, 목은 오가는 응답을 옆에서 보며 등록된 미디어를 기억해 둔다.
그래야 실서버로 올린 사진이 갤러리에 나타난다.

썸네일도 마찬가지다. 서버에는 워커가 없어 `thumbnailUrl`/`originalUrl`/`width`/`height` 가
영영 `null` 이고, 목록 API 도 없어 나중에 다시 물어볼 창구가 없다. 그래서 발급 응답에서
`mediaId → 스토리지 경로`를, R2 로 나가는 PUT 에서 `경로 → 올린 바이트`를 챙겨두고
등록 시점에 이어 붙인다 (`src/mocks/uploadedMedia.ts`). 갤러리에 뜨는 건 **자기가 올린 실물**이다.

서버가 파생값을 채워주기 시작하면 그쪽이 이긴다 — 워커가 붙으면 이 메움은 저절로 비켜난다.

알아둘 제약:

- **새로고침하면 목록이 비워진다.** 목이 메모리에 들고 있을 뿐이다.
- **영상은 썸네일이 없다.** 잡아두는 건 이미지 바이트뿐이다.
- **`photoCount` 배지는 업로드 뒤 갱신되지 않는다.** 방 조회를 다시 부르지 않아
  페이지를 열었을 때 값에 머문다. 목과 무관한 별개 문제다.

서버에 목록 API 가 생기면 `hybrid.ts` 와 `MOCK_MODE` 의 `hybrid` 를 통째로 지운다.

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
