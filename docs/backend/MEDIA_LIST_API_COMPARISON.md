# 미디어 전체 조회와 커서 페이지 조회 비교

## API 선택

| 용도 | API | 응답 |
| --- | --- | --- |
| 화면에 필요한 일부 미디어를 점진적으로 조회 | `GET /rooms/{roomId}/media` | `items`, `nextCursor`, `hasNext`, `totalCount` |
| 내보내기 등 전체 미디어가 한 번에 필요한 작업 | `GET /rooms/{roomId}/media/all` | `items` |

두 API 모두 `folderId`를 선택적으로 받아 방 전체 또는 한 폴더의 미디어를
`createdAt DESC, mediaId DESC`로 정렬한다. 페이지 API는 `uploader`, `cursor`, `size`도
지원한다. 전체 조회 API는 과거 명세와 동일하게 `folderId`만 지원한다.

전체 조회는 미디어 수만큼 응답과 서버의 보유 객체가 커진다. 전체 데이터가 꼭 필요한 기능에만
사용하고, 갤러리처럼 일부부터 노출할 수 있는 기능은 페이지 조회를 사용한다.

## 전체 조회 응답

```json
{
  "data": {
    "items": [
      {
        "mediaId": 1,
        "type": "IMAGE",
        "fileName": "example.jpg",
        "mimeType": "image/jpeg",
        "size": 102400,
        "thumbnailUrl": "https://...",
        "thumbnailUrlExpiresAt": "2026-09-21T12:00:00Z",
        "originalUrl": "https://...",
        "originalUrlExpiresAt": "2026-09-21T12:00:00Z",
        "width": 1920,
        "height": 1080,
        "duration": null,
        "folderIds": [1, 2],
        "uploaderId": 10,
        "uploaderName": "사용자",
        "status": "READY",
        "uploadedAt": "2026-09-21T10:00:00Z"
      }
    ]
  }
}
```

`nextCursor`, `hasNext`, `totalCount`는 포함하지 않는다. 서명 URL과 미디어 처리 상태에 따른
nullable 필드의 규칙은 페이지 조회와 같다.

## 성능 비교

`MediaListModePerformanceTest`를 PostgreSQL 16 Testcontainers에서 실행한 결과다. 각 시나리오를
예열한 뒤 5회 측정한 중앙값을 사용했다. 커서 방식은 페이지 크기 100으로 첫 페이지만 조회하는 경우와
마지막 페이지까지 순회하는 경우를 나눴다. 시간은 로컬 결과이므로 절대값보다 데이터 증가에 따른
경향과 쿼리 수를 본다.

| 미디어 수 | 방식 | 중앙값 | SQL 수 | JSON 크기 | 한 응답의 최대 항목 수 |
| ---: | --- | ---: | ---: | ---: | ---: |
| 100 | 전체 | 11.257 ms | 3 | 40,995 B | 100 |
| 100 | 커서 첫 페이지 | 10.934 ms | 4 | 41,046 B | 100 |
| 100 | 커서 전체 순회 | 9.033 ms | 4 | 41,046 B | 100 |
| 1,000 | 전체 | 22.377 ms | 3 | 412,002 B | 1,000 |
| 1,000 | 커서 첫 페이지 | 8.352 ms | 4 | 41,168 B | 100 |
| 1,000 | 커서 전체 순회 | 89.497 ms | 40 | 412,735 B | 100 |
| 10,000 | 전체 | 86.948 ms | 3 | 4,139,967 B | 10,000 |
| 10,000 | 커서 첫 페이지 | 8.466 ms | 4 | 41,269 B | 100 |
| 10,000 | 커서 전체 순회 | 804.137 ms | 400 | 4,147,652 B | 100 |

화면처럼 첫 페이지면 충분한 경우 커서 조회는 데이터가 100건에서 10,000건으로 늘어도 약 8~11ms와
약 41KB를 유지한다. 전체 조회는 같은 구간에서 약 11ms에서 87ms, 41KB에서 4.14MB로 증가한다.
반대로 전체 데이터를 끝까지 가져오는 조건에서는 전체 조회가 SQL 왕복이 3회로 고정돼 더 빠르다.
커서 전체 순회는 매 페이지의 목록·개수·폴더·업로더 조회가 반복되어 10,000건에서 400쿼리가 필요하다.

JVM 힙 바이트는 GC 시점에 따라 편차가 커 비교 지표에서 제외했다. 대신 응답 JSON 크기와 한 응답이
동시에 보유하는 `MediaDetail` 수를 메모리 압력의 재현 가능한 대리 지표로 기록했다. 전체 조회는
10,000개를 한 번에 조립하지만 커서 조회는 최대 100개만 조립한다.

따라서 전체 목록이 실제로 필요한 서버 작업에는 전체 조회가 적합하고, 사용자 화면과 대규모 방에는
커서 조회가 안전하다. 전체 조회 호출이 빈번해지거나 방의 미디어 상한이 커지면 스트리밍 또는 전용
배치 API로 분리해야 한다.

벤치마크는 일반 테스트에서는 건너뛴다. 다음 명령으로 재실행한다.

```bash
RUN_MEDIA_LIST_MODE_BENCHMARK=true ./gradlew test \
  --tests 'com.sssok.application.media.MediaListModePerformanceTest' --info
```
