# [DownloadController — 단건·다건] 두 구조 비교·부하테스트 기록

> 상위 이슈 #556 의 B 담당 첫 작업. ZIP(요청·상태 조회·압축·만료)은 #582 기록에서 다룬다.
> 결과 문서에는 인증 토큰·서명 URL 원문을 남기지 않는다.

## 1. 작업 개요

- 담당: 현미밥(@gahyeonnni)
- 이슈 / PR: #581 / #587 (구조 이전·보호 테스트·old 측정, 머지 `2f11e96e`) · 이 기록의 new 측정·7·8절은 후속 문서 커밋
- 대상 API·후속 처리:
  - `GET /api/v1/rooms/{roomId}/downloads/media/{mediaId}` — 단건, 서명 GET URL로 302 리다이렉트
  - `POST /api/v1/rooms/{roomId}/downloads/batch` — 다건, 파일별 서명 GET URL 목록 응답
  - 후속 처리 없음 (잡·이벤트·SSE를 만들지 않는 동기 흐름)
- 기존 구조에서 불편했던 실제 사례:
  - 같은 컨트롤러의 단건·다건 서비스가 서로 다른 패키지에 있다. 단건은 `application/media/GetMediaDownloadUrlService`, 다건은 `application/download/CreateBatchDownloadService` 이다.
  - 다건의 대상 해석을 따라가려면 `DownloadTargetResolver`(package-private) → `MediaIdsResolver`(`application/media`) → `FileRepository` 포트 → `FileRepositoryAdapter` 로 패키지 4곳을 거쳐야 한다. 폴더 경로는 여기에 `FolderRepository`·`FolderMediaRepository` 포트와 어댑터가 더해진다.
  - 개수 상한 400 이 두 군데서 난다. 중복 제거한 ID 개수는 DB 조회 전에 `MediaIdsResolver` 의 `TooManyMediaException` 이, 최종 대상 수는 `DownloadTargetResolver` 의 `TooManyFilesException` 이 막는다. 둘 다 `TOO_MANY_FILES(400)` 이라 응답은 같지만, 어느 쪽에서 막혔는지 코드를 두 번 찾아야 한다.
  - 서명 URL 생성 코드(`DownloadFileNames.contentDispositionOf` + `fileStorage.presignGet`)가 단건·다건·ZIP 상태 조회 세 곳에 복제돼 있다.
  - 설정(`DownloadProperties`)은 `infrastructure/config`, 파일명 규칙(`DownloadFileNames`)은 `domain/file` 에 있어 단건·다건 코드가 8개 패키지(2절 목록)에 퍼져 있다.
- 유지해야 할 동작:
  - 단건: 302 + `Location`, 본문 없음. 서명 URL의 Content-Disposition에 ASCII 폴백 `filename` 과 RFC 5987 `filename*` 을 함께 싣는다. 한글 파일명은 `download.<확장자>` 로 폴백한다.
  - 단건: 없음·삭제·다른 방 미디어는 404. 원본이 있는 PROCESSING·READY만 허용하고 RESERVED·FAILED는 404.
  - 다건: `mediaIds`·`folderId` 중 정확히 하나만 허용(아니면 400). `uploader` 는 `folderId` 와만 같이 쓸 수 있고, 지원하지 않는 값은 400.
  - 다건: 해석된 대상이 1000개를 넘으면 400. 다른 방·없는 ID는 조용히 제외하고, 남은 대상이 없으면 404.
  - 다건: 응답 순서는 요청 `mediaIds` 순서(중복 제거)를 따른다. 같은 원본 파일명에는 ` (1)`, ` (2)` 를 붙이고, `expiresAt` = 지금 + `presigned-get-ttl`(기본 5분).
  - 방 존재·만료·입장 검증은 `RoomMembershipInterceptor` 가 계속 먼저 수행한다. 대상 경로는 `/api/v1/rooms/*/downloads/**`.
- 이번 비교에서 확인할 가설:
  - H1. 단건·다건·대상 해석·서명이 한 패키지(`renewal/application/download`)에 모이면 "다운로드 조건 하나 추가" 같은 변경에서 탐색 파일 수와 호출 단계가 줄어든다.
  - H2. 포트와 어댑터를 구체 repository로 합쳐도 외부 연동(`FileStorage`) 분리와 업무 규칙(상태별 다운로드 가능 여부)은 유지된다.
  - H3. 구조 변경만으로는 응답 시간·처리량이 달라지지 않는다. DB 조회와 서명 호출 횟수가 같기 때문이다.

## 2. 두 버전의 기준

- 기존 버전 패키지 / SHA:
  - 패키지: `presentation/api/download`, `application/download`, `application/media`(단건·MediaIdsResolver), `application/port/out`, `infrastructure/persistence/{file,folder}`, `infrastructure/config`, `domain/file`
  - 착수 기준 SHA: `36c85272` (develop, #584 머지 직후. #574 산출물 포함)
  - 기존 버전 비교 기준 SHA: `2dcd71c0` (기존 코드 + 보호 테스트 29건). 이후 추가한 보호 테스트(`31b719ca`, `9a3bfbcf`)도 기존 코드에서 통과를 확인한 뒤 커밋했다(최종 35건).
- 새 버전 패키지 / SHA: `renewal/{incoming,application,domain,outgoing}/download` / 전환 완료 `3ca7404e`
- 커밋 순서 (구조 변경 단위로 나눴다. 각 커밋을 체크아웃해 `--tests '*Download*'` 를 돌려 모두 실패 0 을 확인했다 — 3절 실행 기록)

| 커밋 | 내용 |
| --- | --- |
| `2dcd71c0` test | 단건·다건 API 계약 보호 테스트 29건 (기존 코드 기준) |
| `8bb992e5` refactor | `DownloadFileRepository`·`DownloadFolderRepository` 추가, `DownloadProperties` 를 `renewal/outgoing/config` 로 이동 |
| `31b719ca` test | ID 목록 검증(null·0 이하 400, 빈 목록 404)과 400 메시지 보호 (기존 코드 34건 통과 확인) |
| `6931a9cd` refactor | `DownloadTargetCommand`·`DownloadTargetResolver`·`DownloadUrlIssuer`·예외 4종 추가, `DownloadFileNames` 이동, 기존 리졸버·예외 2종 삭제 |
| `6c4a43d1` refactor | 단건 서비스 `renewal/application/download/single` 이동 |
| `44e2b8d6` refactor | 다건 서비스 `renewal/application/download/batch` 이동, 인자 5개 → Command |
| `c41c4780` refactor | 기존 Controller 에서 zip 두 엔드포인트를 `ZipDownloadController` 로 분리 |
| `9a3bfbcf` test | 본문 없는 다건 요청 400 보호 (기존 코드 35건 통과 확인) |
| `b8480598` refactor | 다건 Request/Response 를 `renewal/incoming/download` 로 이동, `toCommand()` 추가 |
| `3ca7404e` refactor | 단건·다건 `DownloadController` 를 `renewal/incoming/download` 로 이동 (`/api/v1` 직접 표기) |

- 실행 버전 선택 방법: #574와 같은 방식이다. 기존 클래스를 복사하지 않고 `git mv` 로 옮겨 한 시점에는 한 구현만 존재한다. 같은 이름의 서비스가 두 패키지에 동시에 있으면 스프링 빈 이름이 충돌하기 때문이다. 기존 버전은 `2dcd71c0` 을 checkout 해서 실행한다.
- 공유 코드 범위:
  - `renewal/outgoing/storage/FileStorage`·`R2FileStorage`: #574 산출물이다. 인터페이스는 수정하지 않았다. 업로드 쪽 테스트 `R2FileStorageTest` 는 `DownloadFileNames` import 경로 한 줄만 바뀌었다(@Uechann 확인 요청 대상).
  - `DownloadTargetResolver`: 다건과 ZIP이 공유한다. 이번에 새 위치로 옮기면서 기존 ZIP 서비스도 새 Resolver를 호출하게 바꾼다. 전환 후 ZIP 기존 테스트가 전부 통과해야 한다.
  - `MediaIdsResolver`: 폴더 담기·꺼내기, 삭제가 함께 쓴다. 새 Resolver는 이것에 의존하지 않고, 기존 클래스는 수정하지 않는다.
  - `DownloadFileNames`, `DownloadProperties`: ZIP도 쓴다. 581에서 옮기고 ZIP 쪽 import만 바꿨다.
  - ZIP 쪽 변경(581 범위 밖이지만 공유 코드 때문에 필요했던 것): `CreateDownloadJobService` 가 새 Resolver 를 Command 로 호출, `DownloadCompressionWorker`·`GetDownloadJobStatusService` 는 import 경로만 변경, zip 엔드포인트를 `ZipDownloadController` 로 분리. ZIP 기존 테스트는 전부 통과한다. 단 import·예외 클래스 변경으로 `CreateDownloadJobServiceTest`·`SweepDownloadJobsServiceFailureTest` 를 조금 고쳤고, zip Controller 테스트는 `ZipDownloadControllerTest` 로 분리했다(4절 목록).
  - `StoredFile`, `Folder`, `MediaUploaderFilter`: 기존 도메인을 재사용한다.
- 기존·신규 호출 흐름:
  - 기존 단건: `RoomMembershipInterceptor` → `DownloadController.downloadMedia` → `GetMediaDownloadUrlService.getUrl` → `FileRepository.findById` → `FileRepositoryAdapter` → `StoredFileJpaRepository` → `DownloadFileNames.contentDispositionOf` → `FileStorage.presignGet`
  - 기존 다건: `RoomMembershipInterceptor` → `DownloadController.createBatch` → `CreateBatchDownloadService.create` → `DownloadTargetResolver.resolve` → (`MediaIdsResolver.resolve` → `FileRepository.findAllByRoomIdAndIdIn`) 또는 (`FolderRepository.findById` → `FolderMediaRepository.findMediaIdsByFolderId` → `FileRepository.findAllByIdIn`) → `DownloadFileNames.deduplicate` → 파일마다 `contentDispositionOf` + `presignGet`
  - 신규 단건: `RoomMembershipInterceptor` → `renewal.incoming.download.DownloadController.downloadMedia` → `single.GetMediaDownloadUrlService.getUrl` → `DownloadFileRepository.findById`(방·다운로드 가능 상태 확인) → `DownloadUrlIssuer.issue`
  - 신규 다건: `RoomMembershipInterceptor` → `DownloadController.createBatch` → `CreateBatchDownloadRequest.toCommand` → `batch.CreateBatchDownloadService.create` → `DownloadTargetResolver.resolve`(`DownloadTargetCommand.validate` → `DownloadFileRepository`/`DownloadFolderRepository` 조회 → 가능 상태·상한 확인) → `DownloadFileNames.deduplicate` → 파일마다 `DownloadUrlIssuer.issue`
  - 단계 비교: 단건 조회는 서비스 → 포트 → 어댑터 → JPA 에서 서비스 → 저장소 → JPA 로, 다건 대상 해석은 Resolver → `MediaIdsResolver` → 포트 → 어댑터 → JPA 에서 Resolver → 저장소 → JPA 로 줄었다(단건 1단계, 다건 2단계 감소). 서명 코드는 3곳 복제에서 `DownloadUrlIssuer` 1곳으로 모였다(ZIP 상태 조회는 #582 에서 합류).
- 트랜잭션·이벤트·비동기 경계:
  - 기존: 두 API 모두 서비스에 `@Transactional` 이 없다. repository 호출마다 자체 읽기 트랜잭션을 연다. 이벤트와 비동기 처리는 없다. 서명은 로컬 계산이라 네트워크 I/O가 없다.
  - 신규: 같다. 새 서비스·Resolver 에도 `@Transactional` 을 두지 않았고, 이벤트·비동기 처리가 없다.

## 3. 동작 보호

| 보호할 계약 | 테스트 | 기존 버전 | 새 버전 |
| --- | --- | --- | --- |
| 응답·권한 | `DownloadApiTest$단건_다운로드` 12건 — 302·Location·빈 본문, 한글·공백·큰따옴표 파일명의 `filename`/`filename*`, PROCESSING 허용, 다른 업로더 허용, 404(없음·삭제·다른 방·RESERVED·FAILED), 403(비멤버), 401 / `DownloadApiTest$다건_다운로드` 23건 중 400(대상 조건 4종·메시지, 양수 아닌 ID 4종·메시지, 잘못된 uploader, 1001개·메시지, 본문 없음), 404(대상 없음·빈 목록·없는 폴더·다른 방 폴더), 403, 401 | 통과 35/35 — 기존 코드 `2dcd71c0` 에 최종 보호 테스트 파일을 얹어 실행 | 통과 35/35 (`3ca7404e`) |
| 데이터 정합성 | `DownloadApiTest$다건_다운로드` — 파일별 mediaId·fileName·서명 대상 키·Content-Disposition 대응, 요청 순서 유지, 같은 ID 중복 제거, 중복 파일명 번호와 그 이름으로 서명, 다른 방·없는 ID·실물 없는 미디어 제외, 폴더 대상, uploader ME/OTHERS/ALL, expiresAt = 요청 시각 + 5분 | 통과 (위 35건에 포함) | 통과 (위 35건에 포함) |
| 커밋·롤백·비동기 | 해당 없음 — 쓰기·이벤트·비동기 없음 | - | - |
| SSE·자원 정리 | 해당 없음 — 발행하는 SSE 이벤트·생성 자원 없음 | - | - |

기존 테스트는 지우지 않고 renewal 위치로 옮겨 유지했다: `DownloadControllerTest`(`renewal/incoming/download`), `GetMediaDownloadUrlServiceTest`(`.../single`), `CreateBatchDownloadServiceTest`(`.../batch`), `DownloadTargetResolverTest`(`.../shared`), `DownloadFileNamesTest`(`renewal/domain/download`). 바뀐 내용은 4절 목록에 있다.

실행 기록

| 시점 | 명령 | 결과 |
| --- | --- | --- |
| 기존 코드 + 보호 테스트 (`36c85272` 위 작업 트리) | `./gradlew test --tests '*Download*'` | 139건 통과, 실패 0 (ZIP 기존 테스트 포함) |
| 보강 테스트를 기존 코드에서 확인 (`2dcd71c0` 워크트리) | `--tests '*DownloadApiTest'` | 34건 → 35건 통과 |
| 커밋별 재검증 (각 커밋 체크아웃, 별도 워크트리) | `./gradlew test --tests '*Download*'` | `2dcd71c0`·`8bb992e5` 139건, `31b719ca`~`c41c4780` 144건, `9a3bfbcf`~`0e75ca6d` 145건 — 11개 커밋 모두 실패 0 (건수 증가는 보호 테스트 추가 때문) |
| 전환 완료 (`3ca7404e`) | 피드백·관리자·디스코드 테스트 제외 전체 `./gradlew test` (init script 로 제외, 웹훅 빈 값) | 1451건 중 1450건 통과. 실패 1건 `MediaQueryApiTest.이미지_검색_비활성화는_503을_반환한다` 는 develop(`36c85272`)에서도 같은 조건으로 실패해 회귀가 아니다(로컬 `.env` 의 `IMAGE_SEARCH_ENABLED` 가 테스트에 섞이는 환경 문제로 추정) |

실서버 확인 (`3ca7404e`, bootRun + curl)

- 조건: `loadtest-postgres`(pgvector) 에 일회용 DB `sssok_probe` 를 만들어 Flyway 전체 적용. R2 는 더미 자격증명(서명은 로컬 계산이라 외부 호출 없음), 이미지 검색·OpenAI·디스코드는 끔. 확인 후 서버 종료·DB 삭제.
- `sssok-postgres`(postgres:16-alpine)는 pgvector 가 없어 V28 마이그레이션에서 기동이 실패한다.

| 요청 | 결과 |
| --- | --- |
| 단건 READY `제주 바다.jpg` | 302, 서명 URL 의 `response-content-disposition` = `attachment; filename="download.jpg"; filename*=UTF-8''%EC%A0%9C%EC%A3%BC%20%EB%B0%94%EB%8B%A4.jpg` |
| 단건 RESERVED / 없는 ID | 404 `MEDIA_NOT_FOUND` |
| 단건 비멤버 / 인증 없음 | 403 / 401 |
| 다건 `[a.jpg(3), RESERVED, 제주 바다.jpg(1), a.jpg(2), 없는 ID, 3 중복]` | 200, `3 a.jpg` → `1 제주 바다.jpg` → `2 a (1).jpg` 순서, 각 파일명으로 서명, expiresAt 동일 |
| 다건 `{}` / `mediaIds`+`folderId` | 400 `INVALID_PARAM` "다운로드 조건이 올바르지 않습니다" |
| 다건 `[0]` | 400 `INVALID_PARAM` "미디어 ID 목록이 올바르지 않습니다" |
| 다건 `[]` / RESERVED 만 | 404 `MEDIA_NOT_FOUND` |
| 다건 없는 폴더 | 404 `FOLDER_NOT_FOUND` |
| 다건 지원하지 않는 uploader | 400 `INVALID_REQUEST_BODY` |
| 다건 1001개 | 400 `TOO_MANY_FILES` "한 번에 최대 1000개까지 처리할 수 있습니다" |
| 다건 본문 없음 | 400 |
| zip 요청 (기존 경로 유지 확인) | 202 |
| 없는 하위 경로 | 404 |

- 보호 테스트는 처음부터 `renewal/incoming/download/DownloadApiTest` 에 둔다. 전환 전후에 같은 파일을 수정 없이 실행하기 위해서다.
- 픽스처는 전환 후에도 남는 기존 포트(`FileRepository`·`FolderMediaRepository`)와 서비스(`CreateFolderService`·`DeleteMediaService`)로 만든다. 폴더 연결은 트랜잭션이 필요해 `TransactionTemplate` 으로 감쌌다.
- 스토리지는 목이다. 서명 URL에 저장소 키와 Content-Disposition 을 그대로 실어 돌려줘, 응답 URL만 보고도 어느 파일을 어떤 이름으로 서명했는지 확인한다.

## 4. 동일 변경 과제

과제 두 개를 두 구조에 각각 적용했다. 원본 diff 는 `581-change-tasks/` 에 있다(`A-old.patch`, `A-new.patch`, `B-old.patch`, `B-new.patch`). 탐색 파일·시간은 작업 중 기록한 값을 아래 표에 옮겼다.

| | 과제 A | 과제 B |
| --- | --- | --- |
| 유형 | 추가 | 수정 |
| 요구사항 | 다건 다운로드 응답의 파일 항목마다 원본 크기 `sizeBytes` 를 내려준다 | 다건·zip 대상 상한 1000 을 설정값 `download.max-targets`(기본 1000)로 바꾼다. 400 메시지도 설정값을 따른다 |
| 공통 완료 조건 | 응답 JSON 에 `sizeBytes` 가 실제 저장 크기로 나온다(API·Controller 테스트), 기존 다운로드 테스트 전부 통과 | 상한을 2로 설정하면 3개 요청이 `TOO_MANY_FILES` "최대 2개" 로 거절되고 2개는 상한에 걸리지 않는다(새 테스트), 기존 다운로드 테스트 전부 통과 |

- 기존 버전 시작 SHA: `2dcd71c0` / 새 버전 시작 SHA: `3ca7404e`. 종료 SHA 는 만들지 않았다. 실험 브랜치에 커밋하지 않고 작업 트리 diff 를 같은 옵션(`git diff --numstat`, 새 파일은 `git add -N`)으로 집계해 패치로 보관했다.
- 실험 순서: A 는 기존 → 신규, B 는 신규 → 기존 (순서 효과를 반씩 나눔).
- 수행자·숙련도: AI 에이전트(Claude) 가 수행했다. 새 구조를 직접 작성한 직후라 새 구조에 익숙하고, 기존 구조도 같은 세션에서 읽고 옮겼다. 사람 팀원의 탐색 비용과 같다고 볼 수 없다 — 아래 시간은 참고용이며 판단 근거로 쓰지 않는다.

### 변경량

| 지표 | A 기존 | A 신규 | B 기존 | B 신규 |
| --- | --- | --- | --- | --- |
| 운영 코드 추가 / 수정 / 삭제 파일 수 | 0 / 3 / 0 | 0 / 3 / 0 | 0 / 3 / 0 (yml 포함) | 0 / 3 / 0 (yml 포함) |
| 변경 패키지 수 (운영 Java) | 2 (`application.download`, `presentation.api.download`) | 2 (`renewal.application.download.batch`, `renewal.incoming.download`) | 2 (`application.download`, `infrastructure.config`) | 2 (`renewal.application.download.shared`, `renewal.outgoing.config`) |
| 기능 밖의 변경 파일 수 | 0 | 0 | 0 | 0 |
| 운영 코드 추가 / 삭제 줄 | +6 / −3 | +6 / −3 | +15 / −5 | +14 / −5 |
| 추가 / 삭제 클래스·인터페이스 수 | 0 / 0 | 0 / 0 | 0 / 0 | 0 / 0 |
| 수정한 데이터 변환 지점 수 | 2 (서비스 → 결과, 결과 → 응답) | 2 (같음) | 0 | 0 |
| 테스트 변경 파일 수 | 2 | 2 | 2 (1 신규) | 2 (1 신규) |
| 테스트 추가 / 삭제 줄 | +4 / −2 | +4 / −2 | +34 / −1 | +36 / −1 |
| 다운로드 테스트 결과 | 139건 통과 | 145건 통과 | 141건 통과 | 147건 통과 |

(테스트 건수 차이는 시작 SHA 의 보호 테스트 수 차이 — 기존 29건, 신규 35건 — 때문이다.)

### 이해·작업 비용

| 지표 | A 기존 | A 신규 | B 기존 | B 신규 |
| --- | --- | --- | --- | --- |
| 탐색한 고유 파일 수 (운영, 테스트 작성 중 참조한 예외 클래스 제외) | 7 (`DownloadController`, `CreateBatchDownloadResponse`, `BatchDownloadFileResponse`, `BatchDownloadFile`, `CreateBatchDownloadService`, `StoredFile`, `FileSize`) | 5 (`StoredFile`·`FileSize` 는 A 기존에서 이미 확인) | 4 (`DownloadTargetResolver`, `MediaIdsResolver`, `DownloadProperties`, `application.yml`) | 3 (`DownloadTargetResolver`, `DownloadProperties`, `application.yml`) |
| 탐색 범위 (패키지) | 3 (`presentation.api.download`, `application.download`, `domain.file`) | 2 (`renewal.incoming.download`, `renewal.application.download.batch`) | 4 (`application.download`, `application.media`, `infrastructure.config`, resources) | 3 (`renewal.application.download.shared`, `renewal.outgoing.config`, resources) |
| 주요 호출 단계 수 | 4 (Controller → Service → 결과 → 응답) | 4 (같음) | 2 클래스에 상한 검사가 나뉨 (`DownloadTargetResolver` → `MediaIdsResolver`) | 1 클래스 안에서 끝남 (`DownloadTargetResolver`) |
| 간접 참조 탐색 횟수 | 0 | 0 | 1 (사전 상한 검사가 `MediaIdsResolver.resolve(.., maxCount)` 에 위임돼 있음) | 0 |
| 변경 위치 탐색 → 구현 완료 | 18초 | 13초 | 19초 | 25초 |
| 테스트 실행 | 20초 | 18초 | 15초 | 13초 |

- 헷갈린 지점:
  - B 기존: 상한 검사가 두 곳이다. 중복 제거한 ID 개수는 `MediaIdsResolver` 가 `TooManyMediaException` 으로, 최종 대상 수는 `DownloadTargetResolver` 가 `TooManyFilesException` 으로 막는다. 새 테스트를 쓸 때 3개 요청이 어느 예외로 끝나는지 `application/media` 까지 따라가 확인해야 했다. 신규는 예외 하나(`TooManyDownloadFilesException`)라 확인할 곳이 Resolver 하나였다.
  - B 기존: `MediaIdsResolver.resolve(.., maxCount)` 는 다른 기능도 쓰는 공용 메서드라, 상한을 넘기는 방식만 바꾸고 메서드 자체는 건드리지 않아야 한다는 판단이 필요했다.
  - A 는 두 구조 모두 같은 4단계(서비스 결과 record → 응답 record)라 차이가 없었다.
- 측정 한계:
  - 탐색 파일 수는 수행자가 실제로 열어 본 파일을 작업 로그에 적은 값이다. 같은 세션에서 두 구조를 모두 다룬 뒤라, 사람이 처음 볼 때보다 적게 나왔을 수 있다. A 신규의 5개는 A 기존에서 `StoredFile`·`FileSize` 를 이미 본 덕분이다.
  - 두 과제 모두 바꾼 코드 모양이 두 구조에서 같다(패치 비교). 구조 차이는 줄 수보다 "어디까지 따라가야 했는가"(탐색 범위·간접 참조)에서만 드러났다.
  - 삭제 유형 과제는 하지 않았다.
- 정리: 응답 필드 추가(A)는 두 구조의 변경량·단계가 같았다. 대상 규칙 변경(B)은 신규가 한 클래스·한 예외로 끝나 탐색 범위가 1패키지, 간접 참조가 1회 줄었다. 줄 수 차이는 1줄로 의미 없다. 시간은 수행자 특성상 판단 근거로 쓰지 않는다.
- 구조에 의존해 수정해야 했던 테스트: (변경 과제 측정과 별개로, 이번 구조 이전에서 생긴 것을 먼저 적어 둔다)
  - 예외 클래스 이름이 바뀌어 기대값만 바꾼 테스트: `DownloadTargetResolverTest`(4종), `CreateBatchDownloadServiceTest`, `CreateDownloadJobServiceTest`(ZIP), `GetMediaDownloadUrlServiceTest`, `DownloadControllerTest`
  - 설정 클래스 위치가 바뀌어 import 만 바꾼 테스트: `CreateDownloadJobServiceTest`, `SweepDownloadJobsServiceFailureTest`(ZIP)
  - 서비스 시그니처가 Command 로 바뀌어 호출부를 바꾼 테스트: `DownloadTargetResolverTest`(헬퍼 1개), `CreateBatchDownloadServiceTest`(헬퍼 1개, 6곳), `DownloadControllerTest`(목 설정 6곳, 전달 검증 2건)
  - 위치·package 만 바뀐 테스트: `DownloadFileNamesTest`, `R2FileStorageTest`(import), Controller 테스트 분리(`DownloadControllerTest` 11건 / `ZipDownloadControllerTest` 13건)
  - API 계약 테스트 `DownloadApiTest` 는 구조 전환 커밋들에서 한 줄도 수정하지 않았다
- 상대 담당자의 리뷰 의견:

## 5. 부하 조건

| 항목 | 기존 구조 (old) | 새 구조 (new) |
| --- | --- | --- |
| 앱 SHA | `36c85272` (dev `/version` 로 확인) | `2f11e96e` (#587 머지, dev `/version` 로 확인. 백엔드 차이는 #581 변경뿐) |
| 스크립트 | `backend/loadtest` `3e1ce65d` + 실행 스크립트 `run-download.sh` (S3 `sssok/loadtest/sssok-loadtest-581.zip`) | 같음 |
| 대상 서버 | dev `https://dev-api.ssssok.com` (EC2 `[DEV] sssOK`) | 같음 |
| 부하 발생 | EC2 `[LOAD] sssok-load` (같은 리전, arm64 2 vCPU · 메모리 3.8GB, Ubuntu 26.04), k6 v2.3.0, Node 22.22.1, tmux 세션 `dl581` | 같음 |
| 데이터 | 시드 `20261021T1228Z-ba6f` — 방 1개(roomId 134), 계정 30개, 사진 300장(생성 PNG 1280×960, 모두 READY) | 같은 fixtures 재사용 |
| 시나리오 | `media-download.js` single·batch. batch 는 `BATCH_SIZE=10`, `BATCH_FETCH=one`(발급 10장 중 1장만 원본 받기) | 같음 |
| 입력률 | 모드마다 RATE 1 → 3 → 5 → 10, 추가로 20 → 50 요청/초 (`constant-arrival-rate`, VUs = max(10, RATE×5)) | 같음 |
| 예열·측정 | 예열 1분 + 본 측정 3분, 단계 사이 30초 휴식, 각 1회 | 같음 |
| AI·워커 | dev `IMAGE_SEARCH_ENABLED=false` (측정 직전 앱 재시작으로 적용). 썸네일 워커는 기본값 | 같음 (측정 직전 다시 끄고 앱 재시작) |
| 실행 순서 | smoke single → smoke batch → single 1·3·5·10 → batch 1·3·5·10 | 같음 |

- old 추가 실행(RATE 20·50, 같은 조건): single 20·50 = 13:23:28 / 13:28:01, batch 20·50 = 13:32:33 / 13:37:06, 종료 13:41:37. `RATES='20 50' bash run-download.sh old <fixtures>` (new 도 같은 두 번 실행으로 맞춘다).
- new 실행 시각(UTC, 2026-10-22): smoke 15:12:59 → single 1·3·5·10 = 15:13:07 / 15:17:38 / 15:22:09 / 15:26:40 → batch 1·3·5·10 = 15:31:10 / 15:35:41 / 15:40:12 / 15:44:43 → (2차) single 20·50 = 15:49:22 / 15:53:54, batch 20·50 = 15:58:26 / 16:02:58, 종료 16:07:30. 같은 fixtures, 같은 두 번 실행.
- old 실행 시각(UTC, 2026-10-21): smoke 12:38:51 → single 1·3·5·10 = 12:38:58 / 12:43:29 / 12:47:59 / 12:52:30 → batch 1·3·5·10 = 12:57:01 / 13:01:32 / 13:06:02 / 13:10:33, 종료 13:15:05. 각 단계는 시작 + 1분 예열 + 3분 본 측정이다(Grafana 는 각 시작 +1분 ~ +4분 구간을 본다).
- 시드 경위: 처음 시드할 때 dev 의 AI 분석이 켜져 있어 사진 300장 업로드 시 분석 300건이 발생했다(`--allow-ai-cost`). 측정 전에 AI 를 끄고 앱을 재시작해 측정 중에는 분석 부하가 없다. new 측정에서는 같은 fixtures 를 쓰므로 업로드·분석이 다시 생기지 않는다.
- dev 설정 변경: dev 서버 배포 폴더의 `.env` 를 `.env.before-581-loadtest-<시각>` 로 백업 후 `IMAGE_SEARCH_ENABLED=false` 로 변경, `docker compose ... up -d --no-deps app` 으로 앱만 재생성(약 20초 중단, 팀 공지 후 진행). 측정이 모두 끝나면 원복 여부를 팀과 정한다.

## 6. 부하 결과

본 측정(main 3분) 구간의 서버 API 지표다. 원본 받기(`storage_get`, R2)는 구조와 무관해 따로 적는다.

| 모드 · RATE | 지표 | 기존 구조 (old) | 새 구조 (new) | 개선 후 |
| --- | --- | --- | --- | --- |
| single · 1 | 요청 / 실제 요청률 | 180 / 1.00 req/s | 181 / 1.01 req/s | - |
| | p95 / p99 / 평균 / 최대 (ms) | 37.45 / 74.55 / 25.95 / 208.13 | 28.72 / 47.28 / 22.89 / 51.79 | - |
| single · 3 | 요청 / 실제 요청률 | 541 / 3.01 req/s | 541 / 3.01 req/s | - |
| | p95 / p99 / 평균 / 최대 (ms) | 37.93 / 104.32 / 20.56 / 155.76 | 22.44 / 45.17 / 17.98 / 254.17 | - |
| single · 5 | 요청 / 실제 요청률 | 901 / 5.01 req/s | 901 / 5.01 req/s | - |
| | p95 / p99 / 평균 / 최대 (ms) | 31.55 / 75.91 / 18.19 / 211.36 | 20.98 / 47.52 / 15.52 / 144.99 | - |
| single · 10 | 요청 / 실제 요청률 | 1800 / 10.00 req/s | 1801 / 10.01 req/s | - |
| | p95 / p99 / 평균 / 최대 (ms) | 24.76 / 52.84 / 15.86 / 151.58 | 18.75 / 37.63 / 14.53 / 419.47 | - |
| single · 20 | 요청 / 실제 요청률 | 3601 / 20.01 req/s | 3601 / 20.01 req/s | - |
| | p95 / p99 / 평균 / 최대 (ms) | 23.23 / 50.54 / 14.85 / 214.35 | 15.32 / 31.96 / 12.66 / 133.51 | - |
| single · 50 | 요청 / 실제 요청률 | 9001 / 50.01 req/s | 9000 / 50.00 req/s | - |
| | p95 / p99 / 평균 / 최대 (ms) | 26.18 / 66.91 / 15.88 / 580.81 | 15.35 / 34.34 / 12.66 / 196.55 | - |
| batch · 1 | 요청 / 실제 요청률 | 181 / 1.01 req/s | 181 / 1.01 req/s | - |
| | p95 / p99 / 평균 / 최대 (ms) | 33.84 / 112.99 / 24.36 / 212.36 | 41.22 / 81.64 / 25.93 / 115.57 | - |
| batch · 3 | 요청 / 실제 요청률 | 540 / 3.00 req/s | 541 / 3.01 req/s | - |
| | p95 / p99 / 평균 / 최대 (ms) | 33.66 / 91.82 / 20.83 / 138.17 | 22.98 / 42.91 / 17.95 / 71.07 | - |
| batch · 5 | 요청 / 실제 요청률 | 901 / 5.01 req/s | 901 / 5.01 req/s | - |
| | p95 / p99 / 평균 / 최대 (ms) | 31.88 / 60.68 / 19.23 / 115.40 | 21.34 / 35.85 / 16.84 / 118.22 | - |
| batch · 10 | 요청 / 실제 요청률 | 1801 / 10.01 req/s | 1800 / 10.00 req/s | - |
| | p95 / p99 / 평균 / 최대 (ms) | 27.23 / 56.27 / 17.95 / 129.71 | 21.10 / 37.96 / 16.17 / 102.62 | - |
| batch · 20 | 요청 / 실제 요청률 | 3601 / 20.01 req/s | 3601 / 20.01 req/s | - |
| | p95 / p99 / 평균 / 최대 (ms) | 24.68 / 52.83 / 17.20 / 166.66 | 21.46 / 181.25 / 21.25 / 1504.45 | - |
| batch · 50 | 요청 / 실제 요청률 | 9001 / 50.01 req/s | 9000 / 50.00 req/s | - |
| | p95 / p99 / 평균 / 최대 (ms) | 26.17 / 67.82 / 17.42 / 222.80 | 20.07 / 35.98 / 16.00 / 339.41 | - |

| 지표 | 기존 구조 (old) | 새 구조 (new) | 개선 후 |
| --- | --- | --- | --- |
| HTTP·업무 실패율 | 12단계 모두 0% / 0% | 12단계 모두 0% / 0% | - |
| 누락된 반복 수 | 12단계 모두 0 (RATE 50 에서도 부하 발생기 한계 없음) | 12단계 모두 0 | - |
| 판정 기준 | 12단계 모두 통과 | 12단계 모두 통과 | - |
| R2 원본 받기 p95 (`storage_get`, 참고) | 191~255ms | 150~204ms | - |
| CPU·메모리·DB 대기 | 아래 Grafana 표 — 호스트 CPU 최대 30.6%(batch 50), 힙 최대 145MB, HikariCP pending 0, RDS CPU 최대 10.8% | 아래 Grafana 표 — 호스트 CPU 최대 31.2%(batch 50), 힙 최대 131MB, HikariCP pending 0, RDS CPU 최대 8.0% | - |
| 기능별 완료·대기 시간 | 해당 없음 (동기 API) | | - |
| 관련 SSE 전달 결과 | 해당 없음 | 해당 없음 | 해당 없음 |

서버 쪽 지표 — 기존 구조 old (Grafana Prometheus, `environment="dev"`, 각 단계 본 측정 3분 구간, 15초 간격 평균 / 최대)

| 단계 | 호스트 CPU % | 앱 프로세스 CPU % | 힙 사용 MB | HikariCP active | HikariCP pending | 커넥션 획득 ms | 다운로드 API req/s | 서버 측 p95 ms | 5xx |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| single 1 | 4.2 / 6.6 | 2.4 / 4.5 | 101 / 138 | 0 / 0 | 0 / 0 | 0.4 / 0.5 | 1.0 | 22.4 / 27.3 | 0 |
| single 3 | 5.2 / 6.4 | 3.0 / 4.1 | 104 / 145 | 0 / 0 | 0 / 0 | 0.2 / 0.3 | 2.9 | 15.0 / 20.4 | 0 |
| single 5 | 6.4 / 8.3 | 4.0 / 8.9 | 105 / 135 | 0 / 0 | 0 / 0 | 0.1 / 0.1 | 4.9 | 11.0 / 12.5 | 0 |
| single 10 | 8.3 / 9.4 | 4.7 / 6.8 | 96 / 137 | 0 / 0 | 0 / 0 | 0.0 / 0.0 | 10.0 | 9.6 / 10.3 | 0 |
| batch 1 | 3.4 / 4.1 | 1.7 / 3.0 | 95 / 144 | 0 / 0 | 0 / 0 | 0.3 / 0.4 | 1.0 | 23.0 / 43.3 | 0 |
| batch 3 | 6.0 / 7.7 | 3.5 / 5.6 | 103 / 143 | 0 / 0 | 0 / 0 | 0.1 / 0.2 | 3.0 | 15.6 / 18.5 | 0 |
| batch 5 | 6.1 / 8.3 | 3.6 / 10.5 | 84 / 129 | 0 / 0 | 0 / 0 | 0.0 / 0.1 | 5.0 | 13.8 / 16.0 | 0 |
| batch 10 | 8.4 / 9.2 | 4.8 / 6.6 | 87 / 128 | 0.5 / 1 | 0 / 0 | 0.0 / 0.0 | 10.0 | 11.9 / 13.0 | 0 |
| single 20 | 10.6 / 11.6 | 4.8 / 6.0 | 90 / 132 | 0 / 0 | 0 / 0 | 0.0 / 0.0 | 19.7 | 9.1 / 10.4 | 0 |
| single 50 | 22.3 / 23.3 | 10.5 / 12.2 | 96 / 110 | 0.5 / 1 | 0 / 0 | 0.0 / 0.0 | 50.0 | 9.5 / 13.1 | 0 |
| batch 20 | 13.9 / 15.3 | 7.9 / 9.2 | 84 / 129 | 0 / 0 | 0 / 0 | 0.0 / 0.1 | 20.0 | 11.7 / 13.1 | 0 |
| batch 50 | 29.8 / 30.6 | 18.2 / 19.6 | 98 / 136 | 0.4 / 1 | 0 / 0 | 0.0 / 0.0 | 49.8 | 12.3 / 13.4 | 0 |
| 측정 전 유휴 (12:21~12:24) | 1.5 / 1.6 | 0.2 / 0.3 | 314 / 341 | 0 / 0 | 0 / 0 | 0.5 / 0.6 | - | - | - |

서버 쪽 지표 — 새 구조 new (같은 쿼리·같은 구간 방식)

| 단계 | 호스트 CPU % | 앱 프로세스 CPU % | 힙 사용 MB | HikariCP active | HikariCP pending | 커넥션 획득 ms | 다운로드 API req/s | 서버 측 p95 ms | 5xx |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| single 1 | 4.0 / 4.9 | 2.2 / 3.9 | 92 / 122 | 0 / 0 | 0 / 0 | 0.5 / 0.5 | 1.0 | 23.4 / 41.5 | 0 |
| single 3 | 5.4 / 6.2 | 3.1 / 4.3 | 92 / 129 | 0.1 / 1 | 0 / 0 | 0.1 / 0.1 | 3.0 | 14.6 / 17.2 | 0 |
| single 5 | 6.9 / 8.6 | 4.1 / 9.1 | 91 / 128 | 0.1 / 1 | 0 / 0 | 0.1 / 0.1 | 5.0 | 11.7 / 13.6 | 0 |
| single 10 | 8.5 / 9.7 | 5.1 / 9.3 | 87 / 121 | 0 / 0 | 0 / 0 | 0.1 / 0.1 | 9.9 | 10.0 / 15.7 | 0 |
| batch 1 | 3.8 / 4.6 | 2.0 / 2.9 | 91 / 131 | 0 / 0 | 0 / 0 | 0.6 / 0.8 | 1.0 | 33.6 / 43.3 | 0 |
| batch 3 | 7.0 / 9.7 | 4.8 / 10.2 | 91 / 128 | 0 / 0 | 0 / 0 | 0.1 / 0.2 | 3.0 | 18.1 / 22.1 | 0 |
| batch 5 | 6.2 / 8.0 | 3.7 / 6.1 | 92 / 130 | 0 / 0 | 0 / 0 | 0.0 / 0.1 | 4.9 | 14.6 / 18.7 | 0 |
| batch 10 | 9.0 / 10.2 | 5.3 / 6.3 | 88 / 122 | 0 / 0 | 0 / 0 | 0.0 / 0.0 | 9.8 | 13.7 / 15.7 | 0 |
| single 20 | 10.4 / 10.8 | 4.7 / 5.3 | 98 / 125 | 0.8 / 1 | 0 / 0 | 0.0 / 0.0 | 19.9 | 8.4 / 9.4 | 0 |
| single 50 | 22.2 / 23.6 | 10.7 / 13.5 | 85 / 126 | 0 / 0 | 0 / 0 | 0.0 / 0.0 | 49.5 | 8.1 / 8.8 | 0 |
| batch 20 | 14.6 / 17.8 | 9.0 / 17.5 | 84 / 115 | 0.6 / 1 | 0 / 0 | 0.1 / 0.2 | 19.7 | 23.8 / 61.5 | 0 |
| batch 50 | 30.2 / 31.2 | 18.9 / 20.3 | 90 / 128 | 0 / 0 | 0 / 0 | 0.0 / 0.0 | 49.1 | 12.2 / 13.0 | 0 |
| 측정 전 유휴 (15:06~15:09) | 1.5 / 1.6 | 0.3 / 0.5 | 77 / 95 | 0 / 0 | 0 / 0 | 0.5 / 0.8 | - | - | - |

- 쿼리: 호스트 CPU `100*(1-avg(rate(node_cpu_seconds_total{mode="idle"}[1m])))`, 앱 CPU `process_cpu_usage`, 힙 `jvm_memory_used_bytes{area="heap"}`, `hikaricp_connections_active/pending`, 획득 시간 `hikaricp_connections_acquire_seconds_sum/count`, 다운로드 API 는 `http_server_requests_seconds{uri=~".*/downloads/(media/.*|batch)"}` 의 rate·histogram p95.
- 서버 측 p95 는 서버 안에서 잰 처리 시간이라 k6 p95(네트워크 포함)보다 10~15ms 낮다. 다운로드 API 요청률이 k6 실제 요청률과 같아 다른 서버를 친 것이 아님을 확인했다.
- DB(RDS) 자체 지표는 Grafana 에 없어 아래 CloudWatch 표로 따로 본다. 커넥션 풀 기준으로는 old·new 모두 대기(pending)가 한 번도 생기지 않았다.

DB 지표 — 기존 구조 old (CloudWatch `AWS/RDS`, `sssok-db` db.t4g.micro — dev·prod 공용, 각 단계 본 측정 3분, 1분 간격 평균 / 최대)

| 단계 | DB CPU % | 연결 수 | 읽기 지연 | 쓰기 지연 | 읽기 IOPS | 여유 메모리 |
| --- | --- | --- | --- | --- | --- | --- |
| single 1 | 5.4 / 6.2 | 30 / 30 | 0.87 / 0.92ms | 1.11 / 3.05ms | 11.3 / 17.3 | 99MB |
| single 3 | 5.3 / 6.5 | 30 / 30 | 1.04 / 1.40ms | 0.90 / 2.48ms | 11.8 / 19.8 | 100MB |
| single 5 | 6.3 / 7.1 | 30 / 30 | 0.78 / 1.04ms | 1.69 / 2.68ms | 10.4 / 16.7 | 98MB |
| single 10 | 5.2 / 5.6 | 30 / 30 | 0.67 / 0.87ms | 0.56 / 1.33ms | 8.8 / 15.1 | 103MB |
| single 20 | 5.6 / 6.5 | 30 / 30 | 0.65 / 0.85ms | 1.71 / 3.04ms | 9.7 / 15.6 | 102MB |
| single 50 | 8.2 / 10.8 | 30 / 30 | 1.31 / 1.63ms | 1.44 / 3.80ms | 12.6 / 18.5 | 96MB |
| batch 1 | 5.4 / 6.1 | 30 / 30 | 1.09 / 1.33ms | 1.13 / 2.28ms | 8.9 / 15.0 | 103MB |
| batch 3 | 5.7 / 6.0 | 30 / 30 | 1.11 / 1.21ms | 1.09 / 3.28ms | 9.7 / 17.0 | 97MB |
| batch 5 | 5.9 / 6.3 | 30 / 30 | 0.66 / 0.90ms | 1.44 / 3.17ms | 10.4 / 16.5 | 99MB |
| batch 10 | 5.2 / 5.6 | 30 / 30 | 0.77 / 1.07ms | 0.56 / 1.30ms | 6.2 / 6.4 | 101MB |
| batch 20 | 6.0 / 6.6 | 30 / 30 | 0.82 / 1.16ms | 0.79 / 2.36ms | 11.0 / 16.4 | 102MB |
| batch 50 | 7.2 / 8.7 | 30 / 30 | 0.42 / 0.77ms | 1.36 / 2.49ms | 10.7 / 17.9 | 100MB |

DB 지표 — 새 구조 new (같은 방식)

| 단계 | DB CPU % | 연결 수 | 읽기 지연 | 쓰기 지연 | 읽기 IOPS | 여유 메모리 |
| --- | --- | --- | --- | --- | --- | --- |
| single 1 | 5.6 / 6.1 | 30 / 30 | 0.99 / 1.28ms | 1.68 / 3.09ms | 11.6 / 17.3 | 103MB |
| single 3 | 6.4 / 7.9 | 30 / 30 | 0.63 / 0.86ms | 1.55 / 1.90ms | 11.4 / 16.9 | 100MB |
| single 5 | 5.1 / 5.8 | 30 / 30 | 0.62 / 0.86ms | 1.29 / 3.13ms | 9.9 / 16.6 | 102MB |
| single 10 | 5.7 / 6.2 | 30 / 30 | 0.74 / 1.23ms | 1.51 / 3.37ms | 8.8 / 15.4 | 100MB |
| single 20 | 5.3 / 5.7 | 30 / 30 | 0.36 / 0.62ms | 0.63 / 1.89ms | 3.9 / 5.2 | 115MB |
| single 50 | 6.9 / 7.4 | 30 / 30 | 0.79 / 1.16ms | 0.91 / 2.34ms | 6.5 / 12.8 | 115MB |
| batch 1 | 5.1 / 5.6 | 30 / 30 | 0.58 / 1.05ms | 1.68 / 2.72ms | 9.5 / 15.8 | 99MB |
| batch 3 | 5.0 / 5.9 | 30 / 30 | 1.23 / 1.88ms | 0.63 / 1.90ms | 6.3 / 6.5 | 98MB |
| batch 5 | 6.0 / 6.1 | 30 / 30 | 1.03 / 1.43ms | 0.32 / 0.97ms | 8.6 / 8.7 | 99MB |
| batch 10 | 5.3 / 5.4 | 30 / 30 | 0.88 / 1.50ms | 0.10 / 0.29ms | 7.7 / 9.7 | 94MB |
| batch 20 | 6.2 / 7.1 | 30 / 30 | 0.72 / 0.89ms | 1.38 / 3.04ms | 6.7 / 10.4 | 114MB |
| batch 50 | 7.5 / 8.0 | 30 / 30 | 0.42 / 0.67ms | 0.75 / 2.25ms | 8.1 / 14.2 | 108MB |

- 연결 수 30 은 dev·prod 앱의 커넥션 풀이 잡아 둔 연결이라 요청률과 무관하게 일정하다. 요청률을 50배 올려도 DB CPU 는 5% → 8~11% 로 조금 오를 뿐이고 지연은 1ms 안팎이다.
- 조회는 [LOAD] EC2 에서 `aws cloudwatch get-metric-statistics` 로 했다(스크립트 `rds_metrics.py`, 결과 JSON 의 시작 시각 + 2분 ~ + 5분 구간). 이 조회가 new 측정 single 5·10 단계(2026-10-22 15:24~15:32 UTC)와 겹쳐 부하 발생기에서 잠깐 CPU 를 썼다.

- prod 영향: 측정 구간에 prod 5xx 는 없었다. prod 는 요청이 드물어 p95 표본이 거의 없다(구간당 1개 점, 4.8~27.7ms).
- 원본 결과 위치: S3 `sssok/loadtest/results/581/results-581-old-20261021T1315Z.tgz`(old RATE 1~10) · `results-581-old-20261021T1341Z.tgz`(old RATE 20·50) · new 는 `results-581-new-…Z.tgz` 두 묶음(RATE 1~10, 20·50, 마지막 `results-581-new-20261022T1607Z.tgz`) (단계별 결과 JSON·k6 원본·실행 로그). [LOAD] EC2 `~/sssok-581/loadtest/results/` 에도 남아 있다.
- 관찰(old): RATE 1 → 50 에서 k6 p95 는 24~38ms, 서버 측 p95 는 9~23ms 로 오히려 내려가거나 평평하다. RATE 50 에서도 호스트 CPU 22~31%, 커넥션 대기 0, 누락 0 이라 **이 범위에서는 포화점이 보이지 않았다.** CPU 는 요청률에 비례해 오르고(다건이 단건보다 약 1.3배), 다건은 한 요청에 서명 10개를 만들어 CPU 를 더 쓴다. 낮은 RATE 에서 p95 가 높은 것은 표본이 적어 드문 지연의 영향이 큰 탓으로 보인다. single 50 의 최대 581ms 는 한 건의 이상치다.
- 관찰(new): 12단계 모두 실패·누락 0. k6 p95 는 15~41ms 로 old(23~38ms)보다 대체로 5~11ms 낮았지만, 서버 측 p95 차이는 1~3ms 이고 CPU·힙·커넥션·RDS 는 old 와 같은 수준이다. 두 측정은 하루 차이(new 는 dev 재시작 직후)·각 1회 실행이라 k6 p95 차이를 구조 효과로 보지 않는다(8절). 단계 사이 편차의 예로 new batch 20 에 최대 1,504ms 단발 지연이 있었다(7절).
- 조건 차이와 측정 한계: 각 단계 1회 실행이라 실행 간 편차를 모른다. dev 는 단일 인스턴스·공유 RDS 라 같은 시간대의 다른 사용자 요청이 섞일 수 있다. batch 는 발급 10장 중 1장만 원본을 받는다(`BATCH_FETCH=one`).

## 7. 문제와 개선

구조 이전으로 생긴 회귀는 찾지 못했다. 대신 기존 측정(한 번에 10장)으로는 드러나지 않던 **다건 대상 수에 비례하는 CPU 비용**을, 폴더 전체 크기(300장)로 요청하는 추가 실험에서 확인했다. 이 비용은 기존·새 구조가 같은 방식(사진마다 서명 URL 생성)으로 처리해 구조와 무관한 기존 문제다.

### 추가 실험 — 다건 300장 (새 구조 `2f11e96e`, 2026-10-22)

- 조건: 같은 시드 방(사진 300장 전부), `DOWNLOAD_MODE=batch`, `BATCH_SIZE=300`, `BATCH_FETCH=none`(원본은 받지 않고 서버 부하만 본다), RATE 1 → 2 → 4 → 8, 각 예열 1분 + 본 측정 3분, dev AI 꺼짐.
- 실행 시각(UTC): 16:08:04 / 16:12:35 / 16:17:05 / 16:21:36, 종료 16:26. 결과 S3 `sssok/loadtest/results/581/results-581-batch300-20261022T1626Z.tgz`.

| 다건 장수 · RATE | k6 p95 / p99 / 평균 / 최대 (ms) | 서버 평균 / p95 (ms) | 앱 CPU % | 호스트 CPU % | GC 정지 ms/s | 커넥션 대기 | RDS CPU % | 실패 · 누락 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 300 · 1 | 138.3 / 200.2 / 120.8 / 275.4 | 111 / 132 | 5.8 | 7.4 | 1.4 | 0 | 4.7 | 0 · 0 |
| 300 · 2 | 125.7 / 149.5 / 114.1 / 217.2 | 108 / 125 | 10.9 | 12.8 | 2.7 | 0 | 4.8 | 0 · 0 |
| 300 · 4 | 137.9 / 164.3 / 117.1 / 277.1 | 110 / 132 | 21.9 | 24.2 | 5.6 | 0 | 6.0 | 0 · 0 |
| 300 · 8 | 135.9 / 163.2 / 115.9 / 511.9 | 110 / 132 | 43.2 | 46.3 | 10.4 | 0 | 5.3 | 0 · 0 |
| (참고) 10 · 10 | 21.1 / 38.0 / 16.2 / 102.6 | 11 / 14 | 5.3 | 9.0 | 1.0 | 0 | 5.3 | 0 · 0 |
| (참고) 10 · 50 | 20.1 / 36.0 / 16.0 / 339.4 | 10 / 12 | 18.9 | 30.2 | 2.8 | 0 | 7.5 | 0 · 0 |

해석:
- 300장 요청은 부하와 관계없이 서버에서 **건당 약 110ms** 걸린다. 요청률을 8배로 올려도 처리 시간이 늘지 않으므로 대기가 아니라 요청마다 하는 계산 시간이다.
- 앱 CPU 는 **300장 요청 1건/초마다 약 5.4%p** 씩 정비례로 오른다(10장 요청은 1건/초당 약 0.4%p). 사진 수가 30배일 때 처리 시간은 약 11배, CPU 는 약 14배다. 사진마다 하는 일(서명 URL 생성 · Content-Disposition 파일명 · 응답 항목 직렬화)이 비용의 대부분이라는 뜻이다.
- 이 추세면 dev(2 vCPU)는 300장 다건 요청 **초당 약 17건**에서 CPU 가 포화된다. 같은 서버에서 목록 조회도 함께 처리하므로 실제 여유는 더 작다. 열람 URL 에서는 같은 원인(요청마다 반복 서명)으로 dev CPU 가 97% 까지 찬 기록이 있다(#541, `docs/backend/MEDIA_LIST_PRESIGNED_URL_PERFORMANCE.md`). 그 개선은 열람 URL 에만 적용됐고 다운로드 서명은 대상에서 빠졌다.
- DB 는 원인이 아니다. 요청당 쿼리는 3개(방·멤버·파일 IN 조회)이고, 커넥션 대기 0, RDS CPU 5~6% 로 평평했다.
- 보조 근거(로컬, 노트북): 서명 1회 약 59µs(임시 측정 테스트, 5만 회 평균). 다건 응답 시간은 대상 수에 정비례했다(1·10·100·500·1000장 = 3·3·17·59·124ms 중앙값, 1000장 응답 626KB). 로컬 JFR 로 CPU 중 서명 비중을 나눠 보려 했으나 분류가 정리되지 않아 근거로 쓰지 않았다 — 개선 이슈에서 프로파일로 다시 확인한다.

| 증상 | 기존 문제/회귀 | 원인 근거 | 수정 | 재측정 결과 |
| --- | --- | --- | --- | --- |
| 다건 요청 1건의 처리 시간·CPU 가 대상 수에 정비례(300장 건당 110ms, 1건/초당 CPU 5.4%p) | 기존 문제 (두 구조 모두 사진마다 서명 URL 을 새로 만든다) | 위 300장 실험, 로컬 서명 비용·대상 수별 응답 시간, #541 의 같은 원인 기록 | 개선 이슈 #590 에서 다룬다. 이번 PR 범위 밖 | 개선 후 같은 조건(300장 RATE 1·2·4·8 + 10장 기준 측정)으로 재측정 예정 |
| new batch 20 의 최대 1,504ms·p99 181ms (같은 단계 p95 21ms) | 판단 보류 — 1회 실행의 단발 지연 | old batch 20(최대 167ms)·new batch 50(최대 339ms)에서는 재현되지 않음. 서버 측 p95 최대 61.5ms 로 같은 구간에 서버 쪽 지연도 있었음 | 없음 | 개선 후 재측정 때 같은 단계에서 다시 본다 |

- 추가한 회귀 방지 테스트: `DownloadApiTest` 35건(3절)과 각 커밋별 다운로드 테스트 통과(2·3절). 구조 이전 자체로 생긴 성능 회귀는 없어 성능 회귀 테스트는 추가하지 않았다.
- 기존 구조에도 적용 가능한 개선인지: 그렇다. 서명 URL 재사용·서명 비용 축소는 구조와 무관하므로, 개선 효과를 새 구조의 효과로 설명하지 않는다(#556 원칙). 개선 PR 은 구조 이전과 별도 변경 단위로 둔다.
- 해결하지 못한 문제와 영향:
  - 위 대상 수 비례 CPU 비용 — 큰 폴더를 여러 명이 동시에 받으면 dev 기준 초당 십수 건에서 CPU 가 찬다. 개선 이슈로 추적한다.
  - 사용자가 체감하는 다운로드 시간의 대부분은 서버가 아니라 R2 원본 전송이다(원본 받기 p95 150~255ms/장, 서버 API p95 15~40ms). 서버 개선 범위 밖이다.
  - 프론트엔드 개별 저장은 파일 사이 400ms 간격(`INDIVIDUAL_SAVE_GAP_MS`)을 두어 30장이면 대기만 약 12초다. 브라우저의 연속 다운로드 차단을 피하려는 값이라 프론트엔드 쪽에서 따로 판단한다.

## 8. 최종 판단

- 어떤 변경에서 어느 구조가 유리했는가: 응답 필드 추가(과제 A)는 두 구조의 변경량·호출 단계가 같았다. 대상 규칙 변경(과제 B)은 새 구조가 한 클래스(`DownloadTargetResolver`)·한 예외로 끝나 탐색 범위가 1패키지, 간접 참조가 1회 줄었다. 대상 해석·서명처럼 여러 API 가 함께 쓰는 규칙을 바꿀 때 새 구조가 유리했다.
- 수정 비용·이해도 차이: 변경 줄 수 차이는 1줄로 의미 없다. 차이는 "어디까지 따라가야 하는가"에서 났다. 기존 구조는 다건 대상 해석이 `DownloadTargetResolver` → `MediaIdsResolver`(다른 패키지) → 포트 → 어댑터로 이어지고 상한 예외도 두 종류였다. 새 구조는 단건·다건·대상 해석·서명이 `renewal/{incoming,application,domain,outgoing}/download` 아래에 모였고 서명 코드가 한 곳(`DownloadUrlIssuer`)이다. 단, 수행자(AI)가 두 구조를 같은 세션에서 다뤄 사람의 첫 탐색 비용보다 작게 나왔을 수 있다(4절 측정 한계).
- 성능·안정성 차이: RATE 1~50 의 12단계 모두 두 구조 실패·누락 0, 판정 통과. 서버 CPU·힙·커넥션·RDS 는 같은 수준이다(예: batch 50 호스트 CPU old 30.6% / new 31.2%). k6 p95 는 new 가 대체로 5~11ms 낮았지만, 두 측정이 하루 차이(dev 재시작 직후·시간대 다름)이고 각 1회 실행이며 서버 측 p95 차이는 1~3ms 수준이라 **구조 차이에 따른 성능 차이는 없다고 본다.** 구조 이전이 DB 쿼리 수(요청당 3개)와 서명 횟수를 바꾸지 않았다는 점과도 맞는다(가설 H3).
- 불리해진 점: 클래스가 늘었다(`DownloadTargetCommand`·`DownloadUrlIssuer`·다운로드 전용 저장소 2개·예외 4개). 업로드(#557)와 다운로드가 비슷한 이름의 예외·저장소를 따로 가져 이름이 겹친다(`MediaNotFoundException` / `DownloadMediaNotFoundException`, `UploadFileRepository` / `DownloadFileRepository`). zip 은 #582 전까지 기존 위치(`ZipDownloadController`)에 남아 다운로드 코드가 당분간 두 곳에 있다.
- 채택할 구조와 유지할 기존 장점: 새 구조를 채택한다(#587 머지). 유지한 기존 장점 — 외부 스토리지는 `FileStorage` 인터페이스로 분리, 다운로드 가능 상태 판단은 도메인(`UploadStatus.isDownloadable`)에 그대로, 방 입장 확인은 인터셉터 한 곳, 응답 계약과 에러 코드·메시지는 그대로.
- 후속 작업:
  - 다운로드 서명 비용 개선 이슈 #590 — 300장 근거로 서명 URL 재사용·비용 축소, 같은 조건 재측정
  - #582 zip 구조 이전 — 보호 테스트(API 계약·롤백 시 미실행·진행률 커밋·PostgreSQL 만료 정리)부터
  - 상대 담당자(@Uechann) 교차 리뷰 의견 반영(4절)
  - 프론트엔드 개별 저장 간격(400ms)은 프론트엔드 쪽 판단으로 남김
