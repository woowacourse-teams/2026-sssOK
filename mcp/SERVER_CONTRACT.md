# Python 분석 서버 ↔ Spring 계약

2026-11-02 · #644 · Python 구현 완료 범위, Spring 구현은 다음 작업

## 책임

- Spring: 사용자·방 권한, 요청 시점 방 전체 사진 목록, RDS 작업·결과·승인·폴더 반영.
- Python: 받은 사진 다운로드, 분석, 실행 상태·결과 전달, 임시 파일 정리.
- R2: 원본 파일 저장. Spring이 발급한 GET 서명 URL로 Python이 직접 읽는다.
- Python에는 RDS 자격 증명과 SQLite가 없다. `/data/receipts`는 결과 재전송을 위한 실행 영수증이며 사용자용 작업 이력의 기준 저장소가 아니다.

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

사진 수가 100장을 넘으면 전체 요청을 거절하며 임의의 일부 사진만 분석하지 않는다. 다운로드 중 사진당 30MiB·작업당 300MiB를 넘거나 실행이 600초를 넘으면 작업이 실패한다. 사진당 2400만 픽셀 초과는 해당 사진의 준비 오류로 기록하며, 준비 가능한 사진이 있으면 나머지 분석을 진행한다. 큰 방 전체 지원은 별도 부하 검증이 필요하다.

## 로컬 상태 조회

`get_gallery_job(room_id, execution_id)`는 내부 연결 확인용이다. 프론트의 사용자 이력 조회는 Spring/RDS에서 제공한다. 영수증이 만료·유실되면 로컬 조회는 실패한다.

결과 `groups`는 그룹 ID·mediaIds·얼굴 연결 수·신체 보완 수를 포함한다. `unassignedMediaIds`, `errors`, `counts`, `pipelineVersion`을 함께 반환한다. 한 사진은 여러 그룹에 포함될 수 있고 그룹 ID는 실행 안에서만 유효하다. COMPLETED여도 개별 사진 errors가 있을 수 있으므로 Spring은 부분 실패를 표시해야 한다.

## Spring 내부 이벤트 API (제안 계약, 아직 Spring에 구현하지 않음)

Python 설정 `GALLERY_BACKEND_URL`의 고정 URL로 POST한다. 예: `https://backend.internal/internal/gallery-analysis/events`.
MCP 입력으로 임의 callback URL을 받지 않는다. HTTPS가 기본이며 로컬 통합 테스트만 loopback HTTP를 허용한다.

요청 헤더: `Authorization: Bearer <별도의 GALLERY_BACKEND_TOKEN>`.
본문: 위 상태 응답 필드에 `eventId`를 추가한다. 원본 URL·파일 경로는 포함하지 않는다. RUNNING 상태는 약 15초 주기로 진행·생존을 전달한다. terminal 상태 변화는 재시도 대기와 별도로 전달한다.

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

- 작업은 단일 워커가 처리한다. 결과를 로컬 영수증에 기록한 뒤 원본 사본·변환 이미지·특징·상세 근거 등 작업 디렉터리를 삭제한다.
- 완료·실패 영수증에는 입력 URL을 남기지 않는다. 결과·fingerprint·실행 식별값은 재전송과 중복 검사용으로 보관한다.
- 전송 실패는 2초부터 최대 60초 간격으로 재시도한다. terminal 이벤트 ID와 본문은 재전송 시 동일하다.
- 수신 확인되지 않은 결과는 자동 만료하지 않는다. 영수증 상한(기본 1000)에 도달하면 새 작업을 거절해 무제한 누적을 막는다.
- 수신 확인된 영수증·결과는 기본 24시간 뒤 정리한다. 이 기간 이후의 중복 방지는 Spring 책임이다.
- `/data` 볼륨을 유지한 재시작은 대기 작업과 미전송 결과를 복구한다. 실행 중 중단은 FAILED/SERVER_RESTARTED로 보고한다. 재분석은 Spring이 새 실행 UUID·회차로 요청한다.
- 볼륨 유실·호스트 장애까지 로컬 영수증이 보장하지 않는다. Spring은 마지막 생존 시각/실행 기한으로 유실을 감지하고 RDS의 대상 목록에서 재시도해야 한다.
- 실행 UUID가 현재 실행인지 확인하는 것은 Spring 책임이다. 분산 워커의 정확히 한 번 실행을 보장하는 구현이 아니다.
- 과거 SQLite 파일은 자동 변환·삭제하지 않는다. 기존 로컬 실험 데이터라면 보존 여부를 확인하고 별도로 정리한다.

## 배치와 검증 모드

실서비스 모드에는 `GALLERY_BACKEND_URL`과 별도 토큰이 필요하다. Spring 구현 전 직접 분석을 시험할 때만 `GALLERY_STANDALONE=true`로 명시한다. 이 모드는 RDS에 결과를 저장하지 않는다.

이번 단계는 Python과 테스트용 Spring 수신기의 계약 검증이다. 실제 R2 자격 증명 연결, Spring/RDS 구현, 사용자 승인 API, 화면, EC2 배포는 다음 작업이다.
