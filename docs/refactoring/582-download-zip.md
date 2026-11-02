# [DownloadController — ZIP] 두 구조 비교·부하테스트 기록

> 상위 이슈 #556 의 B 담당 두 번째 작업. 선행 작업은 #581(단건·다건) 기록을 본다.
> 결과 문서에는 인증 토큰·서명 URL 원문을 남기지 않는다.

## 1. 작업 개요

- 담당: 현미밥(@gahyeonnni)
- 이슈 / PR: #582 / (PR 생성 후 기입)
- 대상 API·후속 처리:
  - `POST /api/v1/rooms/{roomId}/downloads/zip`: ZIP 요청. 202 응답과 함께 QUEUED 잡을 만든다.
  - `GET /api/v1/rooms/{roomId}/downloads/zip/{jobId}`: ZIP 상태 조회. READY일 때만 서명 URL을 준다.
  - 후속 처리
    - `DownloadJobRequestedEvent` → `AFTER_COMMIT` + `@Async("applicationTaskExecutor")` 압축 워커
    - 만료 정리 스케줄러 (`download.sweep-cron`, 기본 10분마다)
- 기존 구조에서 불편했던 실제 사례:
  - 잡 하나의 수명을 따라가려면 클래스 8개를 오가야 한다.
    - 요청: `CreateDownloadJobService`
    - 이벤트: `DownloadJobRequestedEvent` → `DownloadJobEventListener`
    - 압축: `DownloadCompressionWorker` → `DownloadJobTransitions`(package-private)
    - 상태 조회: `GetDownloadJobStatusService`
    - 만료: `infrastructure/scheduler/DownloadJobSweepBatch` → `SweepDownloadJobsService` → `DownloadJobExpirer`
  - 스케줄러는 `infrastructure/scheduler`, 메트릭은 `common/monitoring`, 영속화는 포트 `application/port/out/DownloadJobRepository` + `infrastructure/persistence/download` 어댑터와 JPA repository 2개로 흩어져 있다.
  - 상태 전이 규칙이 두 곳에 나뉘어 있다. 허용 전이는 `DownloadJobStatus` enum의 Set이 정하고, 전이 시 함께 바뀌는 필드(readyAt·progress·failureReason)는 `DownloadJob` 이 바꾼다.
  - 비동기 실행 풀 이름(`applicationTaskExecutor`)과 실제 설정(`spring.task.execution`, `media-worker-*`)의 연결은 리스너 주석으로만 설명돼 있다.
- 유지해야 할 동작:
  - 요청
    - `mediaIds`·`folderId` 중 정확히 하나만 허용하고(400), 대상 1000개 초과 400, 대상이 없으면 404 — #581과 같은 Resolver를 쓴다.
    - 요청자 행을 잠근 뒤 QUEUED·RUNNING 잡 수가 `max-concurrent-jobs-per-requester`(기본 3) 이상이면 429. 요청자가 없으면 401.
    - 잡 생성 시점의 대상 mediaId를 `download_job_media` 에 고정한다. 응답 `mediaCount` 는 실제 압축 대상 수와 같다.
    - 응답 `fileName` 은 `sssOK_{roomId}.zip`, `totalSize` 는 원본 크기 합이다.
  - 비동기: 생성 트랜잭션이 커밋된 뒤에만 워커가 돈다. 롤백되면 돌지 않는다. 워커는 요청 스레드가 아닌 `media-worker-*` 스레드에서 실행된다.
  - 압축
    - QUEUED → RUNNING → READY | FAILED 로 전이한다.
    - 압축 중에는 트랜잭션을 잡지 않고, 전이는 짧은 트랜잭션으로 각각 커밋한다.
    - 진행률은 바이트 기준 정수 %로, 값이 바뀔 때마다 별도 트랜잭션으로 커밋한다. READY가 되면 100이다.
    - 같은 이름의 엔트리에는 번호를 붙인다. 원본 바이트를 그대로 보존한다.
    - 실패하면 멀티파트 업로드를 `abort()` 하고 FAILED와 사유를 남긴 뒤 ERROR 로그를 찍는다. 스토리지 연결 자체가 실패해도 FAILED로 끝난다.
    - zip 키는 `rooms/{roomId}/downloads/{jobId}.zip` 이다.
  - 상태 조회
    - 본인 외 403, 없는 jobId 404.
    - READY 시점 + `retention`(기본 1시간)이 지나면 410.
    - READY일 때만 `downloadUrl`·`expiresAt` 을 채우고, 조회할 때마다 남은 보관 시간만큼 새로 서명한다.
  - 만료 정리
    - 보관 기간이 지난 READY 잡의 zip 실물을 먼저 지운 뒤 EXPIRED로 바꾼다.
    - 한 잡이 실패해도 나머지는 계속 정리하고, 실패한 잡은 다음 회차에 다시 시도한다. 다시 돌려도 결과가 같다(멱등).
  - 기존 결함은 그대로 둔다: #109(동시 생성 제한 레이스), #113(상태 조회 roomId 검증 누락). 현재 동작을 테스트로 고정하고 정책을 바꾸지 않는다.
- 이번 비교에서 확인할 가설:
  - H1. 요청 → 압축 → 조회 → 만료가 `renewal/application/download/zip/*` 아래에 모이면 "ZIP 상태 하나 추가"나 "보관 정책 변경" 같은 변경에서 탐색 비용이 줄어든다.
  - H2. 상태 전이를 상태 패턴(`renewal/domain/download`)으로 옮겨도, 긴 I/O 동안 트랜잭션을 잡지 않는 구조와 커밋 후 비동기 경계는 그대로 유지된다.
  - H3. 구조 변경만으로는 API 접수 시간·작업 대기·최종 완료 시간이 달라지지 않는다.

## 2. 두 버전의 기준

- 기존 버전 패키지 / SHA:
  - 패키지: `presentation/api/download`, `application/download`, `application/port/out/DownloadJobRepository`, `infrastructure/persistence/download`, `infrastructure/scheduler/DownloadJobSweepBatch`, `common/monitoring/DownloadProcessingMetrics`, `domain/download`
  - 착수 기준 SHA: `acd50977` (develop, #581 머지 이후)
  - 기존 버전 비교 기준 SHA: `c04002a5` (보호 테스트·버전 의도 커밋까지)
- 새 버전 패키지 / SHA: (전환 완료 커밋 SHA 기입)
  - `renewal/incoming/download`: `DownloadController`(단건·다건과 합침), 요청·응답 DTO, `DownloadJobEventListener`, `DownloadJobSweepBatch`
  - `renewal/application/download/zip`: 요청·압축·상태 조회·만료 서비스와 `DownloadJobTransitions`, 결과·이벤트 record
  - `renewal/application/download/exception`: 403·404·410·429 예외
  - `renewal/domain/download`: `DownloadJob`, `status/`(상태 패턴 5종), `exception/IllegalDownloadJobStatusException`
  - `renewal/outgoing/database`: `DownloadJobRepository`(포트·어댑터를 하나의 클래스로), JPA 엔티티·저장소 2쌍
  - `renewal/outgoing/monitoring/DownloadProcessingMetrics`
- 실행 버전 선택 방법: #574·#581과 같은 방식이다. 전환 후 기존 구현을 삭제하므로 리스너·워커·스케줄러가 한 벌만 등록된다. 기존 버전은 비교 기준 SHA를 checkout 해서 실행한다.
- 공유 코드 범위:
  - `FileStorage.openDownloadStream`·`openUploadStream`(`AbortableOutputStream`)·`presignGet`·`delete`: #574 산출물이며 수정하지 않는다.
  - `DownloadTargetResolver`, `DownloadFileNames`, `DownloadProperties`: #581에서 renewal로 옮긴 것을 재사용한다.
  - `applicationTaskExecutor`(`ApplicationTaskExecutorConfig`, `spring.task.execution`): 스토리지 정리·알림과 함께 쓰는 공용 풀이며 설정을 바꾸지 않는다.
  - `renewal/outgoing/database/member/MemberRepository.lockById`: 요청자 잠금에 쓴다. 인증·회원 쪽과 함께 쓰며 수정하지 않는다.
  - `DownloadFileRepository.findAllByIdIn`, `DownloadUrlIssuer`: #581 산출물. 압축 대상 조회와 상태 조회 서명에 재사용한다(동작은 기존 `FileRepository.findAllByIdIn`·`presignGet` 직접 호출과 같다).
  - `MemberActivityRepository.transferDownloadJobs`: 익명→회원 병합에서 `DownloadJobJpaRepository.transferRequester` 를 쓴다. import 경로만 바뀐다.
  - DB 스키마 `download_job`, `download_job_media`(V16): 스키마를 바꾸지 않는다.
- 기존·신규 호출 흐름:
  - 기존 요청: `RoomMembershipInterceptor` → `ZipDownloadController.createZip` → `CreateDownloadJobService.create`(@Transactional) → `DownloadTargetResolver.resolve` → `MemberRepository.lockById` → `DownloadJobRepository.countByRequesterIdAndStatusIn` → `save` → `saveJobMedia` → `publishEvent(DownloadJobRequestedEvent)`
  - 기존 압축: (커밋 후) `DownloadJobEventListener.handle`(@Async + AFTER_COMMIT) → `DownloadCompressionWorker.compress` → `DownloadJobTransitions.markRunning` → `findMediaIdsByJobId` → `FileRepository.findAllByIdIn` → `openUploadStream` → 파일마다 `openDownloadStream` → `ZipOutputStream` → `updateProgress` → `markReady` (실패 시 `abort` → `markFailed`)
  - 기존 상태 조회: `ZipDownloadController.zipStatus` → `GetDownloadJobStatusService.getStatus` → `findById` → 본인·만료 확인 → (READY) `contentDispositionOf` + `presignGet`
  - 기존 만료: `DownloadJobSweepBatch.sweep`(@Scheduled) → `SweepDownloadJobsService.sweepExpired` → `findAllExpiredReady` → 잡마다 `DownloadJobExpirer.expire`(@Transactional: `fileStorage.delete` → `markExpired` → `save`)
  - 신규 요청: `RoomMembershipInterceptor` → `DownloadController.createZip` → `CreateDownloadJobRequest.toCommand` → `CreateDownloadJobService.create(DownloadTargetCommand)`(@Transactional) → 이후 기존과 같다. 진행 중 상태 목록은 `DownloadJobStatus.active()` 가 정한다.
  - 신규 압축: `incoming/download/DownloadJobEventListener.handle`(@Async + AFTER_COMMIT) → `zip/DownloadCompressionWorker.compress` → (기존과 같은 전이 순서) / 대상 조회는 `DownloadFileRepository.findAllByIdIn`
  - 신규 상태 조회: `DownloadController.zipStatus` → `GetDownloadJobStatusService.getStatus` → 본인·만료 확인 → (`status.isReady()`) `DownloadUrlIssuer.issue`
  - 신규 만료: `incoming/download/DownloadJobSweepBatch.sweep` → `SweepDownloadJobsService.sweepExpired` → 잡마다 `DownloadJobExpirer.expire` (기존과 같다)
  - 상태 전이: 기존은 `DownloadJobStatus` enum 의 허용 Set + `DownloadJob.transitionTo` 두 곳, 신규는 각 상태 객체가 자기에게 허용된 전이(`run`·`ready`·`fail`·`expire`)만 구현하고 나머지는 기본 메서드가 `IllegalDownloadJobStatusException` 을 던진다. DB 값과 응답 문자열은 `name()` 으로 같다.
- 트랜잭션·이벤트·비동기 경계:
  - 기존
    - 요청은 트랜잭션 하나로 처리한다: 대상 조회 + 요청자 행 잠금 + 개수 확인 + 잡·대상 저장 + 이벤트 등록.
    - 워커 메서드 자체에는 트랜잭션이 없다. 전이(RUNNING·progress·READY·FAILED)마다 별도 빈의 짧은 트랜잭션에서 커밋한다.
    - 만료는 잡마다 트랜잭션 하나다. 이 트랜잭션 안에서 스토리지 삭제(네트워크 I/O)를 수행한다.
  - 신규: 위 경계를 그대로 유지했다. 요청 트랜잭션 하나, 워커 무트랜잭션 + 전이마다 `DownloadJobTransitions` 의 짧은 트랜잭션, 만료는 잡마다 트랜잭션 하나. 만료 트랜잭션 안의 I/O는 구조 개편에서 손대지 않고 7절 후보로 남긴다.

## 3. 동작 보호

| 보호할 계약 | 테스트 | 기존 버전 | 새 버전 |
| --- | --- | --- | --- |
| 응답·권한 | `ZipDownloadApiTest` `요청`·`상태_조회` — 요청 202·응답 필드, 400(조건 3종·1000 초과), 404(잡 미생성), 429(끝난 잡은 한도에서 제외), 401, 미입장 403 / 상태 조회 401·403·404·410, 실패 사유, 진행 중엔 URL 없음, READY에서만 남은 보관 시간만큼 새로 서명 | 통과 | 통과 |
| 데이터 정합성 | `ZipDownloadApiTest` — `download_job_media` 고정 대상 = mediaCount(다른 방·없는 ID 제외), 생성 후 대상 상태가 바뀌어도 고정 목록으로 압축, 같은 이름 엔트리 번호, 원본 바이트 보존 | 통과 | 통과 |
| 커밋·롤백·비동기 | `ZipDownloadApiTest` `압축_진행`, `ZipDownloadBoundaryTest` — 커밋 후 비동기로 READY / 워커가 `media-worker-*` 스레드에서 실행 / **롤백 시 잡·워커 모두 없음** / **파일마다 진행률이 커밋돼 압축 도중 상태 조회에 보임** / 원본 읽기·업로드 스트림 실패 시 abort + FAILED | 통과 | 통과 |
| SSE·자원 정리 | `ZipDownloadBoundaryTest`(PostgreSQL) — 만료 READY 잡의 zip 삭제 + EXPIRED, 보관 중 READY·RUNNING은 그대로, **한 잡 실패 시 나머지 계속**, 실패한 잡은 다음 회차에 재정리. ZIP은 SSE 이벤트를 발행하지 않는다(발행하는 이벤트는 내부용 `DownloadJobRequestedEvent` 뿐이고, 클라이언트는 상태 조회 폴링으로 진행을 확인한다) | 통과 | 통과 |

굵게 표시한 항목은 기존에 테스트가 없던 계약이다. 만료 정리 기존 테스트는 H2(`@H2IntegrationTest`)와 Mock 기반이라 PostgreSQL로 보강했다.

- 기존 버전 실행: develop `acd50977` 기준, `ZipDownloadApiTest` 24건·`ZipDownloadBoundaryTest` 3건 모두 통과(2026-11-02).
- `경로의_방이_잡의_방과_달라도_본인_잡이면_조회된다`는 #113(상태 조회 roomId 검증 누락)의 현재 동작을 고정한 것이다. 이번 비교에서는 정책을 바꾸지 않으며, #113 수정 시 이 테스트를 함께 바꾼다.
- 새 버전 실행: 전환 후 같은 27건 통과. 기존 ZIP 테스트 9종은 `renewal/{application/download/zip,incoming/download,domain/download}` 로 옮겨 그대로 통과하고, `ZipDownloadControllerTest` 는 컨트롤러를 합치며 `renewal/incoming/download/DownloadControllerTest` 에 합쳤다. 다운로드·병합·메트릭 관련 208건 통과(2026-11-02).
- 로컬 실서버(bootRun + MinIO + PostgreSQL, 보관 20초·정리 5초 주기)에서 실제 요청으로 확인: 업로드 3건 → ZIP 202 → RUNNING → READY(100) → 서명 URL로 받은 zip 엔트리 `same.png`·`same (1).png`·`other.png` 와 원본 바이트 일치 → 다른 사람 403·없는 잡 404·조건 400·대상 없음 404·인증 없음 401 → 보관 경과 후 410, DB `EXPIRED`, 스토리지 zip 삭제.

기존 테스트(전환 후 위치를 옮겨 그대로 유지할 것): `CreateDownloadJobServiceTest`, `DownloadCompressionWorkerTest`, `DownloadJobAsyncTriggerTest`, `GetDownloadJobStatusServiceTest`, `SweepDownloadJobsServiceTest`, `SweepDownloadJobsServiceFailureTest`, `DownloadJobSweepBatchTest`, `DownloadJobSweepBatchSchedulingTest`, `DownloadJobTest`

## 4. 동일 변경 과제

- 유형: (선정 후 기입)
- 요구사항:
- 공통 완료 조건:
- 기존 버전의 시작 / 종료 SHA:
- 새 버전의 시작 / 종료 SHA:
- 수행자·실험 순서·기존 숙련도:

### 변경량

| 지표 | 기존 구조 | 새 구조 |
| --- | --- | --- |
| 운영 코드 추가 / 수정 / 삭제 파일 수 |  |  |
| 변경 패키지 수 |  |  |
| 기능 밖의 변경 파일 수 |  |  |
| 운영 코드 추가 / 삭제 줄 |  |  |
| 추가 / 삭제 클래스·인터페이스 수 |  |  |
| 수정한 데이터 변환 지점 수 |  |  |
| 테스트 변경 파일 수 |  |  |
| 테스트 추가 / 삭제 줄 |  |  |

### 이해·작업 비용

| 지표 | 기존 구조 | 새 구조 |
| --- | --- | --- |
| 탐색한 고유 파일 수 |  |  |
| 주요 호출 단계 수 |  |  |
| 간접 참조 탐색 횟수 |  |  |
| 변경 위치 탐색 시간 |  |  |
| 구현 시간 |  |  |
| 테스트 수정 시간 |  |  |

- 헷갈린 지점:
- 구조에 의존해 수정해야 했던 테스트:
- 상대 담당자의 리뷰 의견:

## 5. 부하 조건

- 앱·스크립트 SHA:
- 서버·실행 설정:
- 데이터 수·파일 크기·형식:
- 입력률·동시 사용자·작업 수:
- 예열·측정 시간:
- 캐시·AI·워커·SSE 조건:
- 실행 순서·반복 횟수:

## 6. 부하 결과

| 지표 | 기존 구조 | 새 구조 | 개선 후 |
| --- | --- | --- | --- |
| 실제 처리량 |  |  |  |
| p95 / p99 |  |  |  |
| HTTP·업무 실패율 |  |  |  |
| 누락된 반복 수 |  |  |  |
| CPU·메모리·DB 대기 |  |  |  |
| 기능별 완료·대기 시간 (API 접수 / 작업 대기 / 최종 완료) |  |  |  |
| 관련 SSE 전달 결과 |  |  |  |

- 원본 결과 위치:
- 조건 차이와 측정 한계:

## 7. 문제와 개선

코드를 읽으며 발견한 후보다. 구조 개편 PR에서는 고치지 않고, 부하·검증으로 실제 문제인지 확인한 뒤 별도 변경으로 다룬다.

| 증상 | 기존 문제/회귀 | 원인 근거 | 수정 | 재측정 결과 |
| --- | --- | --- | --- | --- |
| (후보) 서버가 압축 도중 종료되면 잡이 RUNNING에 남는다 | 기존 문제 | QUEUED·RUNNING을 회수하는 스케줄러가 없다. 남은 잡이 동시 진행 한도(3)를 계속 차지해 해당 사용자는 429에 막힌다 | 미정 | - |
| (후보) 공용 풀이 포화되면 잡이 QUEUED에 남는다 | 기존 문제 | `media-worker` 풀(max 8, queue 100)이 거절하면 AFTER_COMMIT 리스너에서 예외가 나고, 재시도 경로가 없다 | 미정 | - |
| (후보) 만료 트랜잭션 안에서 스토리지 삭제 | 기존 문제 | `DownloadJobExpirer.expire` 가 @Transactional 안에서 `fileStorage.delete` 를 호출해, 네트워크 지연만큼 커넥션을 점유한다 | 미정 | - |

- 추가한 회귀 방지 테스트:
- 기존 구조에도 적용 가능한 개선인지:
- 해결하지 못한 문제와 영향:

## 8. 최종 판단

- 어떤 변경에서 어느 구조가 유리했는가:
- 수정 비용·이해도 차이:
- 성능·안정성 차이:
- 불리해진 점:
- 채택할 구조와 유지할 기존 장점:
- 후속 작업:
