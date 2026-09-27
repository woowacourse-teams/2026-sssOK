# 미디어 전체 조회·다건 작업 API 단순화 결과

점검일: 2026-09-26  
관련 이슈: #304  
프런트엔드 연관 이슈: #300

## 변경 결과

현재 갤러리는 미디어 전체 목록을 조회하고, 클라이언트가 선택한 ID를 직접 관리한다. 이에 따라
다건 작업 API에서 `selection.include/exclude`를 제거하고 `mediaIds`를 명시적으로 전달하도록 변경했다.

`GET /rooms/{roomId}/media`의 커서 페이지 API와 커서 코덱, 페이지 저장소 쿼리, 관련 인덱스는
향후 재사용을 위해 유지했다. 과거 selection 도입과 성능 측정 결과는
[`MEDIA_SELECTION_CHANGE_REPORT.md`](./MEDIA_SELECTION_CHANGE_REPORT.md)를 수정하지 않고 역사 기록으로 보존했다.

## API 계약

### 전체 목록

```http
GET /rooms/{roomId}/media/all
GET /rooms/{roomId}/media/all?folderId=31
GET /rooms/{roomId}/media/all?uploader=ME
GET /rooms/{roomId}/media/all?folderId=31&uploader=OTHERS
```

- `folderId`, `uploader`는 각각 또는 함께 사용할 수 있다.
- `uploader`는 `ALL`, `ME`, `OTHERS`를 지원하고 생략하면 `ALL`이다.
- 두 조건을 함께 사용하면 모두 만족하는 미디어만 반환한다.
- 응답에는 `items`만 있고 커서·페이지 메타데이터는 없다.

### 다건 작업

| API | 요청 대상 |
|---|---|
| `DELETE /rooms/{roomId}/media` | `mediaIds` |
| `PUT /rooms/{roomId}/media/folders` | `mediaIds`, `folderId` |
| `DELETE /rooms/{roomId}/media/folders` | `mediaIds`, `folderIds` |
| `POST /rooms/{roomId}/downloads/batch` | `mediaIds` 또는 `folderId` |
| `POST /rooms/{roomId}/downloads/zip` | `mediaIds` 또는 `folderId` |

- `mediaIds`는 중복을 제거하고 요청 순서대로 해석한다.
- null, 0 이하 ID는 400을 반환한다.
- 다른 방이거나 존재하지 않는 ID는 작업에서 제외하고 API 정책에 따라 `notFoundMediaIds`로 알린다.
- 다운로드는 `mediaIds`와 `folderId` 중 정확히 하나만 허용한다.
- 다운로드의 `uploader`는 `folderId` 방식에서만 사용할 수 있다.
- 다운로드 최종 대상은 1,000개, 삭제 실제 대상은 500개로 제한한다.
- 폴더 관계가 실제로 변경된 경우에만 `media.folders.updated` SSE를 발행한다.

## 코드 점검에서 보정한 내용

- 폴더 전체 다운로드에도 최종 대상 1,000개 상한을 적용했다.
- 다운로드 대상 해석 주석의 “둘 다 생략하면 방 전체” 표현을 실제 계약인 “정확히 하나”로 바꿨다.
- 이미 있는 폴더 관계를 다시 추가하거나, 없는 관계를 제거하는 멱등 요청에서는 SSE를 발행하지 않도록 했다.

## 프런트엔드 연동 주의사항

현재 프런트엔드의 삭제·다운로드 API 호출부와 목 핸들러 일부는 아직 `selection`을 전송한다.
이 상태로 새 백엔드와 연동하면 `mediaIds` 누락으로 400을 받는다. 프런트엔드 연관 이슈 #300에서
다음 작업이 함께 반영되어야 한다.

- 갤러리를 `/media/all?folderId=&uploader=` 기반으로 전환한다.
- 선택 상태를 include/exclude가 아닌 실제 `mediaIds` 목록으로 관리한다.
- 삭제, 폴더 추가·제거, batch·zip 다운로드 요청 DTO와 목 핸들러에서 `selection` 호환 분기를 제거한다.
- `mediaIds` 다운로드에는 `uploader`를 넣지 않고, 폴더 전체 다운로드에만 넣는다.

## PostgreSQL 인덱스·실행계획 점검

PostgreSQL 16.14 Testcontainers에 Flyway V1~V24를 적용한 뒤 미디어 100,000건과 폴더 관계 120,000건을
적재했다. `EXPLAIN (ANALYZE, BUFFERS)`로 실제 조회를 수행한 결과다.

| 조회 조건 | 반환 건수 | 실행 시간 | 핵심 계획·인덱스 |
|---|---:|---:|---|
| 방 전체 / `ALL` | 100,000 | 13.468 ms | `idx_stored_file_room_created_id_visible` Index Scan |
| 방 전체 / `ME` | 10,000 | 2.863 ms | `idx_stored_file_room_uploader_created_id_visible` Index Scan |
| 방 전체 / `OTHERS` | 90,000 | 12.158 ms | room-created-id 인덱스 순차 스캔 후 uploader 필터 |
| 폴더 / `ALL` | 20,000 | 33.972 ms | `uk_folder_media` Bitmap Index Scan + Hash Join + Sort |
| 폴더 / `ME` | 10,000 | 12.789 ms | folder unique 인덱스 + uploader 부분 인덱스 |
| 폴더 / `OTHERS` | 10,000 | 18.254 ms | folder unique 인덱스 + Hash Join + Sort |

점검 결과, 방 전체 최신순과 `ME` 조회는 기존 부분 복합 인덱스를 정렬 없이 사용했다.
폴더 조회는 `(folder_id, media_id)` unique 인덱스로 대상을 좁힌 후 조인·정렬했다.
`OTHERS`는 대부분의 행을 반환하는 조건이므로 전용 인덱스보다 기존 최신순 인덱스 스캔이 합리적이다.

재현 명령은 다음과 같다.

```bash
cd backend
RUN_MEDIA_ALL_FILTER_BENCHMARK=true ./gradlew test \
  --tests 'com.sssok.infrastructure.persistence.file.MediaAllFilterQueryPerformanceTest' \
  --rerun-tasks --info
```

본 측정은 로컬 단일 실행 결과로 운영 응답 시간을 보장하지 않는다. 다만 마이그레이션이 생성한 인덱스가
핵심 조회 조건에서 실제로 선택되는지를 재현 가능하게 확인하는 목적이다.
