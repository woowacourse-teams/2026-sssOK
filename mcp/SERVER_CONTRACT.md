# Python 분석 서버 ↔ Spring 계약

2026-11-02 · #644 · Python 구현 완료 범위, Spring 구현은 다음 작업

## 책임

- Spring: 사용자·방 권한, 요청 시점 방 전체 사진 목록, RDS 작업·결과·승인·폴더 반영.
- Python: 받은 사진 다운로드, 분석, 실행 상태·결과 전달, 임시 파일 정리.
- R2: 원본 파일 저장. Spring이 발급한 GET 서명 URL로 Python이 직접 읽는다.
- Python에는 RDS 자격 증명이 없다. `/data/executions-v1.sqlite3`는 실행 상태·결과 재전송을 위한 로컬 SQLite이며 사용자용 작업 이력의 기준 저장소가 아니다. Spring/RDS의 장기 보관 책임은 유지한다.

## MCP 시작 도구

`group_photos_by_person(room_id, job_id, request_id, attempt, photos)`

| 필드 | 책임 |
|---|---|
| room_id | Spring이 접근 권한을 검증한 방 ID |
| job_id | Spring이 RDS에 먼저 생성한 작업 UUID |
| request_id | 실행 UUID. 동일 실행의 네트워크 재시도는 재사용, 새 실행 회차는 새 UUID |
| attempt | Spring이 증가시키는 양의 실행 회차 |
| photos | 방 전체 목록. 각 항목은 mediaId, source(서명 URL), extension |

응답은 `jobId`, `executionId`, `attempt`, `roomId`, `state`, `progress`, `result`, `errorCode`, 시각, `reused`다. `jobId`는 Spring 작업 ID이며 `executionId`는 request_id다. 상태는 QUEUED/RUNNING/COMPLETED/FAILED다.

같은 실행 UUID에 다른 방·작업·회차·사진 목록을 보내면 거절한다. 같은 작업의 실행이 로컬에서 대기·실행 중이면 새 회차도 거절한다. 서명 URL 쿼리 변경만으로 다른 사진으로 판단하지 않지만 접수된 URL을 자동 교체하지 않는다.

사진 수가 200장을 넘으면 전체 요청을 거절하며 임의의 일부 사진만 분석하지 않는다. 다운로드 중 사진당 30MiB·작업당 300MiB를 넘거나 실행이 600초를 넘으면 작업이 실패한다. 사진당 2400만 픽셀 초과는 해당 사진의 준비 오류로 기록하며, 준비 가능한 사진이 있으면 나머지 분석을 진행한다. 큰 방 전체 지원은 별도 부하 검증이 필요하다.

## 거절 응답

접수 도구가 거절하면 `isError=true` 결과를 돌려준다. 실행이 만들어지기 전에 던져지는 일시적 거절에는 `structuredContent`로 `{"errorCode": "...", "retryable": true}`를 함께 싣는다. 이때는 같은 실행 UUID로 나중에 다시 보내도 안전하다(이미 접수된 실행이면 `reused=true`로 받는다).

| errorCode | 상황 |
|---|---|
| QUEUE_FULL | 대기·실행 중인 작업이 `max_queue`(기본 8)에 도달 |
| BACKLOG_FULL | 수신 확인되지 않은 영수증이 상한(기본 1000)에 도달 |
| DEPLOYING | 배포로 새 접수를 멈춘 상태 |
| UNAVAILABLE | 필수 백그라운드 작업 중단으로 접수를 막은 상태(수동 재시작 필요) |
| STORAGE_BUSY | 로컬 저장소 쓰기 잠금을 5초 안에 얻지 못함 |

입력 오류, 같은 실행 UUID의 다른 스냅샷 재사용, 같은 작업의 활성 실행 충돌은 표시 없이 거절한다. 호출자는 `retryable`이 `true`가 아니면 재시도하지 않는다.

## 로컬 상태 조회

`get_gallery_job(room_id, execution_id)`는 내부 연결 확인용이다. 프론트의 사용자 이력 조회는 Spring/RDS에서 제공한다. 영수증이 만료·유실되면 로컬 조회는 실패한다.

결과 `groups`는 그룹 ID·mediaIds·얼굴 연결 수·신체 보완 수를 포함한다. `unassignedMediaIds`, `errors`, `counts`, `pipelineVersion`을 함께 반환한다. 한 사진은 여러 그룹에 포함될 수 있고 그룹 ID는 실행 안에서만 유효하다. COMPLETED여도 개별 사진 errors가 있을 수 있으므로 Spring은 부분 실패를 표시해야 한다.

## Spring 내부 이벤트 API (제안 계약, 아직 Spring에 구현하지 않음)

Python 설정 `GALLERY_BACKEND_URL`의 고정 URL로 POST한다. 예: `https://backend.internal/internal/gallery-analysis/events`.
MCP 입력으로 임의 callback URL을 받지 않는다. HTTPS가 기본이며 로컬 통합 테스트만 loopback HTTP를 허용한다.

요청 헤더: `Authorization: Bearer <별도의 GALLERY_BACKEND_TOKEN>`.
본문: 위 상태 응답 필드에 `eventId`를 추가한다. 원본 URL·파일 경로는 포함하지 않는다. RUNNING 상태는 진행이 바뀌면 약 2초 간격으로, 바뀌지 않으면 약 15초 간격으로 전달한다(생존 확인). 단계 변경은 진행 간격을 기다리지 않는다. terminal 상태 변화는 재시도 대기와 별도로 전달한다. 진행 본문(`progress`의 `stage`·`processed`·`total`·`unit`)과 단계 목록은 [docs/backend/ORGANIZE_PROGRESS_CONTRACT.md](../docs/backend/ORGANIZE_PROGRESS_CONTRACT.md)를 따른다.

Spring은 다음을 확인한다.

1. 서비스 인증, jobId·roomId·executionId·attempt가 현재 실행과 일치하는지.
2. 반환된 모든 mediaId가 저장된 분석 대상에 속하는지.
3. 오래된 RUNNING 이벤트가 완료 상태를 덮어쓰지 않도록 단조로운 상태 전이.
4. 동일 terminal 이벤트 재전송 시 중복 저장·폴더 반영이 없는지.
5. RDS 트랜잭션 커밋 후 아래 응답 반환.

```json
{"accepted": true, "executionId": "전달받은 실행 UUID", "state": "COMPLETED"}
```

HTTP 2xx만으로 성공 처리하지 않는다. accepted=true 및 executionId·state가 요청과 일치해야 한다. RUNNING 확인은 결과 삭제를 허용하는 확인이 아니다.

오래된 실행의 결과는 현재 결과에 적용하지 않는다. Spring이 해당 이벤트를 의도적으로 폐기 처리한 경우에도 위 형식으로 수신 확인해 Python 재전송을 종료할 수 있다.

## 재전송·복구·삭제

- 작업은 단일 워커가 처리한다. 워커가 결과 형식·사진 범위를 검증한 뒤 결과·COMPLETED·미전송 상태를 SQLite에 한 트랜잭션으로 커밋한다. 부모는 성공 상태를 다시 쓰지 않으며, 커밋 전 실패·시간 초과는 RUNNING 상태일 때만 FAILED로 기록한다. 먼저 확정된 완료/실패를 늦은 워커나 부모가 덮어쓰지 않는다.
- 결과 저장 후 원본 사본·변환 이미지·특징·상세 근거 등 작업 디렉터리를 삭제한다. 새로운 실행에서는 `response.json` 파일을 부모에게 전달하지 않는다. 로컬 CLI의 상세 분석 파일은 기존과 동일하다.
- 완료·실패 영수증에는 입력 URL을 남기지 않는다. 결과·fingerprint·실행 식별값은 재전송과 중복 검사용으로 보관한다.
- 전송 실패는 2초부터 최대 60초 간격으로 재시도한다. terminal 이벤트 ID와 본문은 재전송 시 동일하다.
- 수신 확인되지 않은 결과는 자동 만료하지 않는다. 영수증 상한(기본 1000)에 도달하면 새 작업을 거절해 무제한 누적을 막는다.
- 수신 확인된 영수증·결과는 기본 24시간 뒤 정리한다. 이 기간 이후의 중복 방지는 Spring 책임이다.
- `/data` 볼륨을 유지한 재시작은 SQLite에 커밋된 완료 결과와 대기 작업을 유지한다. 커밋되지 않은 RUNNING은 FAILED/SERVER_RESTARTED로 보고한다. 커밋 전에 프로세스가 종료되면 트랜잭션이 롤백되므로 결과만 있고 상태가 RUNNING인 중간 상태는 게시되지 않는다. 재분석은 Spring이 새 실행 UUID·회차로 요청한다.
- 볼륨 유실·호스트 장애까지 로컬 영수증이 보장하지 않는다. Spring은 마지막 생존 시각/실행 기한으로 유실을 감지하고 RDS의 대상 목록에서 재시도해야 한다.
- 실행 UUID가 현재 실행인지 확인하는 것은 Spring 책임이다. 분산 워커의 정확히 한 번 실행을 보장하는 구현이 아니다.
- 기존 `/data/receipts/*.json`은 시작 복구에서 한 트랜잭션으로 최초 한 번 이전한다. 기존 미전송·수신 확인·대기 상태를 유지한다. 기존 RUNNING의 완성된 `response.json`만 형식·사진 범위를 검증해 완료로 복구하고, 없거나 손상됐으면 실패 처리한다. 권한·디스크 오류나 잘못된 영수증은 이전 전체를 롤백하고 시작을 중단한다.
- 이전 완료 표시는 같은 DB 트랜잭션으로 저장하며 이후 재시작에서 JSON을 다시 읽지 않는다. 원본 JSON은 자동 삭제하지 않는 백업이며 최신 상태가 아니다. 입력 URL이 남아 있을 수 있으므로 접근을 제한하고 이전·전송 검증 후 운영자가 보존·삭제를 결정한다.
- DB는 스키마 버전 2를 사용하고 알 수 없는 버전은 열어 갱신하지 않는다. 과거 실험의 다른 SQLite 파일은 자동 변환·삭제하지 않는다. JSON 전용 구버전으로의 자동 롤백은 SQLite 파일 생성 후 거부한다.

## SQLite 저장과 트랜잭션

- Python 표준 `sqlite3`를 사용하며 별도 DB 서버·ORM·추가 패키지는 없다. 분석 이미지·모델·특징 배열은 DB에 넣지 않는다.
- `executions` 테이블은 실행 UUID, Spring 작업 ID·회차, 방·입력 사진, 상태·결과·오류, 시각·수신 확인을 보관한다. 결과와 사진 배열은 JSON 문자열 컬럼으로 저장한다. `metadata`는 기존 JSON 이전 완료를 기록한다.
- 접수와 작업 선점은 `BEGIN IMMEDIATE`로 검사·갱신을 묶는다. 실행 UUID 기본 키와 활성 작업의 부분 유니크 인덱스로 중복을 제한하고, RUNNING이 있으면 다음 작업을 선점하지 않는다.
- 연결은 작업마다 열고 닫아 스레드·프로세스 간에 공유하지 않는다. 쓰기 충돌은 최대 5초 기다린다. 사진 분석·다운로드·HTTP 전송 중 DB 트랜잭션을 잡고 있지 않는다.
- 기본 rollback journal과 `synchronous=EXTRA`를 사용한다. 현재 단일 워커·짧은 쓰기 범위에서는 WAL을 추가하지 않는다. DB와 journal을 보관할 `/data`의 디렉터리 쓰기 권한이 필요하다.
- 로컬 파일시스템의 영속 볼륨을 사용한다. 실행 중 DB 파일 하나만 복사하지 않고 SQLite backup API 또는 모든 프로세스를 중단한 상태에서 `/data` 전체를 백업한다. SQLite는 호스트·볼륨 유실이나 네트워크 전달의 정확히 한 번 실행까지 보장하지 않는다.

공식 근거: [Python sqlite3](https://docs.python.org/3.13/library/sqlite3.html), [트랜잭션과 쓰기 잠금](https://www.sqlite.org/lang_transaction.html), [원자적 커밋](https://www.sqlite.org/atomiccommit.html), [동기화 설정](https://www.sqlite.org/pragma.html#pragma_synchronous).

## 배치와 검증 모드

실서비스 모드에는 `GALLERY_BACKEND_URL`과 별도 토큰이 필요하다. Spring 구현 전 직접 분석을 시험할 때만 `GALLERY_STANDALONE=true`로 명시한다. 이 모드는 RDS에 결과를 저장하지 않는다.

이번 단계는 Python과 테스트용 Spring 수신기의 계약 검증이다. 실제 R2 자격 증명 연결, Spring/RDS 구현, 사용자 승인 API, 화면, EC2 배포는 다음 작업이다.

## 필수 백그라운드 작업 중단

분석 접수 처리 루프 또는 결과 전송 루프가 예외·예상하지 못한 반환·취소로 종료되면
새 분석 접수를 차단하고 healthz·readyz에 503을 반환한다. 종료 감지 전에 이미 접수한 작업은
SQLite에 남으며 시작 복구 대상이다. 장애 원문 대신 작업 이름과 예외 종류만 기록한다.

서버를 자동 종료하거나 다른 작업을 취소하지 않는다. 운영자가 원인을 확인하고 수동으로
재시작한다. 상태 응답 503만으로 Docker가 자동 재시작하지는 않는다.
Spring 통신 실패와 개별 분석 실패는 기존 재시도·실패 처리를 유지한다.
정상 배포 종료는 장애로 취급하지 않으며 standalone에서는 전송 루프를 요구하지 않는다.

재시작 시 커밋된 완료 결과·대기 작업은 유지되고 미완료 RUNNING은 실패 처리된다.
수동 재시작 시 진행 중인 분석은 중단될 수 있다.
이 감시는 작업의 종료를 검출하며, 종료하지 않은 교착이나 이벤트 루프 정지까지 감지하지는 않는다.

## #661: 작업 종류와 중복 사진 결과 (schema v2)

- 기존 도구는 PERSON, 새 `group_similar_photos(room_id, job_id, request_id, attempt, photos)`는 SIMILAR를 저장한다.
- 접수·조회·이벤트의 `jobType`은 PERSON 또는 SIMILAR다. 입력 한도·단일 워커·시간 제한·인증은 동일하다.
- 동일 execution UUID의 종류 변경은 거절한다. 두 종류가 한 대기열을 공유한다.
- SIMILAR 결과: `resultVersion=1`, `pipelineVersion`, `groups[{groupId,mediaIds}]`,
  `smallGroupMediaIds`(2장 후보 배열), `unassignedMediaIds`(단독 사진), `errors`, `counts`.
- 그룹은 최소 3장, 실행 내 ID, 사진 중복 없음. 오류를 포함해 입력 전체를 정확히 분할한다.
- PERSON 결과 형식은 유지한다. SIMILAR에는 얼굴·신체 수를 넣지 않는다.
- SQLite 파일명은 `executions-v1.sqlite3`을 유지하지만 #661에서 `PRAGMA user_version=2`가 되었다(현재는 아래 환경 격리의 v3).
  이전 서버를 중지한 상태에서 단일 트랜잭션으로 kind 컬럼을 추가하고 기존 기록을 PERSON으로 해석한다.
  기존 완료 결과·수신 확인·대기 상태는 유지한다. 기존 RUNNING 복구 정책은 그대로다.
- v1 실행 파일은 v2 DB를 읽을 수 없다. #661의 배포 표시는 sqlite-v2였으며 마이그레이션 가능성이 있는
  배포 실패 후 v1/JSON 이미지로 자동 복귀하지 않는다. 데이터 보존 후 v2 호환 릴리스로 복구한다.
  변경 전 정지 상태 DB 백업으로 돌아가는 경우 신규 접수·미전송 결과 유실을 따로 검토해야 한다.
- 기존 미전송 PERSON 이벤트에도 jobType 필드가 추가되어 이벤트 해시가 바뀔 수 있다.
  Spring 중복 처리는 eventId만이 아니라 executionId·terminal state를 기준으로 해야 한다.

정책·자원 측정·모델 준비: [SIMILAR_PHOTOS.md](SIMILAR_PHOTOS.md).

## DEV·PROD 공유 실행 시 환경 격리

- 같은 MCP endpoint에 서로 다른 서비스 토큰으로 접속한다. 인증된 환경은 HTTP 요청 scope에서
  도구 실행으로 전달되며, 도구의 공개 입력 스키마에는 환경 이름·콜백 URL을 노출하지 않는다.
- 접수 환경을 SQLite v3 실행 기록에 저장한다. 해당 환경만 결과를 조회하거나 동일 실행을 재사용할 수 있다.
- 완료·실패·진행 이벤트는 저장된 환경의 고정 HTTPS endpoint와 전용 콜백 토큰으로 전송한다.
  재시작·재시도 시에도 환경이 보존되며, 미설정 환경을 DEV 등으로 대체하지 않는다.
- 기존 작업은 DEV로 이전한다. 실행 UUID는 전역 유일, business job ID의 동시 실행 제약은 환경별이다.
- 설정과 v3 롤백 제약은 `deployment/README.md`의 공유 환경 절차를 따른다.
