# 업로드 API 목 (MSW)

백엔드 업로드 API 가 나오기 전까지 프론트가 혼자 업로드 흐름을 만들 수 있게 두는 목이다.
핸들러는 `frontend/src/mocks/handlers/upload.ts`, 검증은 `upload.test.ts` 에 있다.

제약의 출처는 [R2_PRESIGNED_UPLOAD.md](../backend/R2_PRESIGNED_UPLOAD.md) 이고,
허용 확장자와 용량 한도는 backend `MediaType` enum 을 그대로 따른다.

---

## 3단계 흐름

```
1) POST {API_PREFIX}/rooms/{roomId}/media/upload-urls   → presigned URL 발급
2) PUT  {uploadUrl}                                     → R2 로 직접 업로드
3) POST {API_PREFIX}/rooms/{roomId}/media/complete      → 완료 확정
```

### 1) 발급

```jsonc
// 요청
{ "folderId": null, "files": [{ "fileName": "한라산.jpg", "contentType": "image/jpeg", "size": 2097152 }] }

// 201 응답
{ "data": [{ "uploadUrl": "https://mock-r2.sssok.dev/sssok-dev/rooms/5031/...", "storageKey": "rooms/5031/...", "contentType": "image/jpeg" }] }
```

- 응답 배열은 요청 순서를 그대로 지킨다. 프론트는 순서로 원본 파일과 짝지운다.
- `contentType` 은 **서버가 확장자로 다시 정한 값**이다. 요청에 실어 보낸 값은 쓰이지 않는다.
- 한 파일이라도 검증에 걸리면 배치 전체를 발급하지 않는다.

| 상황 | 응답 |
| --- | --- |
| 허용하지 않는 확장자 (`heic` 등) | 400 `UNSUPPORTED_MEDIA_TYPE` |
| 사진 10MB 초과 | 413 `FILE_TOO_LARGE` |
| 영상 1GB 초과 | 413 `FILE_TOO_LARGE` |
| 토큰 없음 | 401 `UNAUTHORIZED` |
| 없는 방 번호 | 404 `ROOM_NOT_FOUND` |

허용 확장자: `jpg` `jpeg` `png` `gif` `mp4` `webm` `mov`

### 2) PUT

발급 시점에 Content-Type 이 서명에 들어간다. **발급받은 `contentType` 을 그대로 헤더에 실어야 한다.**

| 상황 | 응답 |
| --- | --- |
| 발급값과 같은 Content-Type | 200 |
| Content-Type 이 다름 | 403 |
| Content-Type 헤더 없음 | 403 |
| 만료된 URL (`X-Amz-Date` + `X-Amz-Expires` 로 판정, TTL 10분) | 403 |
| 발급한 적 없는 storageKey | 403 |
| `Authorization` 을 같이 보냄 | 403 |

마지막 줄이 특히 걸리기 쉽다. R2 로 나가는 PUT 은 `apiClient` 를 타면 안 되고,
토큰 없이 `fetch` 로 직접 보내야 한다.

### 3) 완료 확정

```jsonc
// 요청
{ "storageKeys": ["rooms/5031/mock-upload-1.jpg"] }
```

| 상황 | 응답 |
| --- | --- |
| PUT 까지 끝난 파일 | 201 + `data[]` (`status: "COMPLETED"`) |
| PUT 이 끝나지 않은 파일이 섞임 | 400 `UPLOAD_NOT_COMPLETED` |
| 발급한 적 없는 storageKey | 404 `STORED_FILE_NOT_FOUND` |
| 토큰 없음 | 401 `UNAUTHORIZED` |

---

## 손으로 실패를 확인하는 방법

파일 **이름에 표식을 넣으면** 목이 그 시나리오로 답한다 (`UPLOAD_MOCK_MARKERS`).

| 표식 | 동작 |
| --- | --- |
| `__fail__` | PUT 이 500 으로 실패한다 — 업로드 실패 모달 확인용 |
| `__expired__` | 이미 만료된 URL 을 발급한다 — 만료 403 흐름 확인용 |

예: `제주-해변__fail__.jpg` 를 올리면 발급은 되고 PUT 에서 깨진다.

---

## 백엔드와 합의가 필요한 3가지

목이 먼저 정해둔 값이라, 실제 구현과 어긋나면 **여기가 아니라 서버 쪽을 맞춰야 하는지 먼저 이야기한다.**

1. **발급 요청을 객체로 감쌌다.** `StoredFile.beginUpload` 가 발급 시점에 `folderId` 를 받는데
   문서의 요청 형식(배열)에는 담을 자리가 없다. `{ folderId, files }` 로 감쌌다.
2. **`contentType` 은 서버가 정한다.** `MediaType.fromFileName` 이 파일명 확장자로 타입을 정하므로
   클라이언트가 보낸 `contentType` 은 쓰이지 않는다. 목도 같게 두고 요청 필드는 형식만 맞춰 받는다.
3. **용량 초과는 413 이어야 한다.** `FileSizeExceededException` 이 `GlobalExceptionHandler` 에
   등록돼 있지 않아 지금 구현하면 10MB 초과가 500 으로 나간다. 목은 이슈 #71 명세대로
   413 `FILE_TOO_LARGE` 로 둔다.

경로는 이슈 #71 명세(`/rooms/{roomId}/media/…`)를 따른다. 스파이크 문서의
`/rooms/{code}/files/upload-urls` 와 다르므로 백엔드 구현 시 확인이 필요하고,
완료 확정 경로 `POST /rooms/{roomId}/media/complete` 는 발급 경로에 맞춰 프론트가 정한 이름이다.
