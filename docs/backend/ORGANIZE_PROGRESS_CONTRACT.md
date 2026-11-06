# 사진 정리 진행 정보 계약

2026-11-06 · #704 · 기반: #659 · 프론트 연동: #682

**사용자가 "지금 무엇을, 얼마나 했는지" 알 수 있도록 단계(stage)와 처리 수를 약 2초 간격으로 전달한다.**
이 문서는 Python 분석 서버 → Spring → 프론트(조회 API·SSE)까지 진행 정보의 이름과 의미를 확정한다.
문구는 프론트가 정한다. 아래 문구는 표시 예시다.

## 1. 전달 정책 (Python → Spring)

| 상황 | 동작 |
| --- | --- |
| 같은 단계에서 처리 수가 늘어남 | 기본 **2초**마다 최신값 한 번 전송 |
| 단계가 바뀜 | 진행 전송 간격을 기다리지 않고 다음 전달 루프에서 전송 |
| 진행 정보가 그대로임 | 기본 **15초**마다 생존 확인만 전송 |
| 완료·실패 | 진행 전송 제한 없이 바로 전송. 수신 확인 전까지 재전송 |
| 전송 실패 | 기존 재시도(2초~최대 60초)·수신 확인 유지. 단계가 바뀌어도 재시도 대기는 건너뛰지 않음 |

- 간격은 설정값이다(`GALLERY_PROGRESS_INTERVAL_SECONDS`=2, `GALLERY_HEARTBEAT_INTERVAL_SECONDS`=15).
- 사진마다 HTTP 요청을 보내지 않는다. 분석 코드는 진행을 파일에 기록하고, 별도 전달 루프가 최신값만 보낸다.
- "바뀌었다"의 기준은 `stage`·`processed`·`total`·`unit`이다. 경과 시간은 기준이 아니다.
- **생존 확인은 "전달 프로세스가 살아 있다"는 신호**이며 분석이 전진했다는 뜻이 아니다.
  Spring의 `lastEventAt`만 갱신한다.

## 2. 이벤트 본문 (Python → Spring)

```json
{
  "jobType": "PERSON",
  "state": "RUNNING",
  "progress": { "stage": "detecting_faces", "processed": 24, "total": 60, "unit": "PHOTO" }
}
```

- 수치가 없는 단계는 `stage`만 보내고 `processed`·`total`·`unit`은 `null`이다.
- 기존 `progress.counts.processed/total` 형태도 Spring이 한동안 함께 받는다(Spring 먼저 배포하는 동안 이전 Python과 호환).
- Spring은 `processed`가 `total`보다 크거나 음수이면 수치를 버리고 단계만 저장한다. `unit`은 `PHOTO`·`BODY` 외에는 `null`로 저장한다.

## 3. 단계 목록

`processed`·`total`은 **그 단계 안에서만** 센다. 단계가 바뀌면 0부터 다시 시작할 수 있고, 전체 진행률이 아니다.
`unit`은 센 대상이다. 사진 수(`PHOTO`)와 검출된 신체 수(`BODY`)는 다른 단위다.

### 공통 (PERSON·SIMILAR)

| 순서 | stage | 설명 예시 | processed / total / unit |
| --- | --- | --- | --- |
| 1 | `waiting` | 분석 순서를 기다리고 있어요 | 없음 (Spring이 MCP 접수 때 설정) |
| 2 | `downloading_photos` | 사진을 가져오고 있어요 | 내려받은 사진 / 대상 사진 / PHOTO |
| 3 | `initializing` | (바로 지나가는 단계. 문구 불필요) | 없음 |

### PERSON (4번 이후)

| 순서 | stage | 설명 예시 | processed / total / unit |
| --- | --- | --- | --- |
| 4 | `loading_models` | 분석 모델을 준비하고 있어요 | 없음 |
| 5 | `preparing` | 사진을 준비하고 있어요 | 준비한 사진 / 대상 사진 / PHOTO |
| 6 | `detecting_faces` | 얼굴을 찾고 있어요 | 처리한 사진 / 전체 사진 / PHOTO |
| 7 | `grouping_faces` | 같은 사람을 모으고 있어요 | 없음 |
| 8 | `detecting_bodies` | 사람 모습을 찾고 있어요 | 처리한 사진 / 전체 사진 / PHOTO |
| 9 | `extracting_body_features` | 사람 특징을 분석하고 있어요 | 처리한 신체 / 검출된 신체 / **BODY** |
| 10 | `matching_bodies` | 얼굴이 안 보이는 사진을 연결하고 있어요 | 없음 |
| 11 | `exporting` | 정리 결과를 준비하고 있어요 | 없음 |

PERSON의 `loading_models`는 모델 파일 확인(체크섬 계산)과 실행 환경 준비 구간이다.
얼굴·신체 모델은 각 단계(`detecting_faces`, `detecting_bodies`, `extracting_body_features`) 안에서
처음 쓸 때 불러온다. 그래서 그 단계는 첫 사진을 처리하기 전까지 단계만 있고 수치는 `null`이다.

### SIMILAR (4번 이후)

SIMILAR는 모델을 두 번 불러온다. 단계 순서가 뒤로 가지 않도록 모델마다 단계를 따로 둔다.

| 순서 | stage | 설명 예시 | processed / total / unit |
| --- | --- | --- | --- |
| 4 | `preparing` | 사진을 준비하고 있어요 | 준비한 사진 / 대상 사진 / PHOTO |
| 5 | `loading_scene_model` | 분석 모델을 준비하고 있어요 | 없음 |
| 6 | `extracting_scene_features` | 사진 장면을 분석하고 있어요 | 처리한 사진 / 전체 사진 / PHOTO |
| 7 | `loading_center_model` | 분석 모델을 준비하고 있어요 | 없음 |
| 8 | `extracting_center_features` | 사진 구도를 분석하고 있어요 | 처리한 사진 / 전체 사진 / PHOTO |
| 9 | `matching_similar_photos` | 비슷한 사진을 비교하고 있어요 | 비교한 사진 / 전체 사진 / PHOTO |
| 10 | `grouping_similar_photos` | 비슷한 사진을 모으고 있어요 | 없음 |
| 11 | `exporting` | 정리 결과를 준비하고 있어요 | 없음 |

측정할 수 없는 단계에 임의의 퍼센트나 처리 수를 채우지 않는다. 바이트 단위 진행률은 이번 범위가 아니다.
위 순서 번호는 늦은 이벤트를 걸러내는 기준(§5)이며, 목록에 없는 단계는 순서 비교 없이 그대로 받는다.

## 4. 작업 조회 API와 SSE (Spring → 프론트)

기존 `organize.job.updated` 이벤트와 작업 조회 응답의 `progress` 형식을 유지하고 `unit`만 추가한다.
두 곳이 같은 단계·수치·단위를 준다.

```json
{
  "jobId": "550e8400-e29b-41d4-a716-446655440000",
  "jobVersion": 7,
  "attempt": 1,
  "status": "RUNNING",
  "progress": { "stage": "detecting_faces", "processed": 24, "total": 60, "unit": "PHOTO" },
  "retryable": false,
  "retryAvailableAt": null,
  "reviewExpiresAt": null,
  "resultExpiresAt": null
}
```

위 예시는 **전체의 40%가 아니라, 얼굴 분석 단계에서 사진 60장 중 24장을 처리했다**는 뜻이다.

| 필드 | 의미 |
| --- | --- |
| stage | 현재 처리 단계. `progress`가 있으면 항상 있다 |
| processed / total / unit | 현재 단계의 처리 수 / 전체 수 / 단위. 수치가 없는 단계는 `null` |
| jobVersion | 작업이 바뀔 때마다 늘어남. 오래된 SSE·조회 응답을 구분 |
| attempt | 재분석 실행 회차 |

### progress가 없는 경우

- 접수 직후 아직 단계가 정해지기 전에는 `progress` 키가 **생략**된다(NON_NULL 직렬화).
- **READY에서는 `progress`를 비워 생략한다.** 마지막 중간 수치가 완료 상태에 남지 않는다.
- FAILED는 마지막 진행을 그대로 둔다.
- 재시도(FAILED → QUEUED)는 진행을 비운다.

### 프론트 처리 규칙

1. `status`가 **READY**가 되면 완료로 본다. 이전 진행값을 지우고 결과 조회 API를 호출한다. 그룹 전체는 SSE에 싣지 않는다.
2. `processed`·`total`·`unit`이 `null`이거나 `progress`가 없어도 깨지지 않아야 한다.
3. 모르는 `stage`는 진행 바를 앞뒤로 움직이지 않고 이전 값을 유지한다.
4. 상세 진행은 **요청자에게만** 전달된다. 다른 참여자는 방 점유 정보만 받는다.

### 재접속

SSE를 다시 구독하고 `GET current`로 스냅샷을 받는다. 스냅샷의 `jobVersion`보다 작거나 같은 SSE 이벤트는 버린다.
이 방식은 기존과 같다.

## 5. Spring 수신 규칙

| 상황 | 처리 |
| --- | --- |
| 상태·단계·처리 수·전체 수·단위가 현재와 모두 같음 | **생존 시각(`lastEventAt`)만 갱신.** `jobVersion` 증가와 SSE 발행 생략 |
| 첫 RUNNING 이벤트(상태가 다름) | 진행 수치가 없어도 저장하고 발행 |
| MCP 접수 응답 | 작업이 아직 QUEUED일 때만 RUNNING + `waiting`으로 바꿈. Python 진행이 먼저 도착했다면 덮어쓰지 않음 |
| 이전 실행 회차·다른 작업의 이벤트 | 폐기하고 수신 확인(기존 동작) |
| **같은 회차의 지연 이벤트** | 단계 순서가 현재보다 앞이거나, 같은 단계에서 `processed`가 줄었으면 폐기하고 수신 확인 |
| 완료·실패 | 기존대로 처리. 수신 확인 전까지 Python이 재전송 |

QUEUED/RUNNING의 시작 지연·생존 시간 초과·실행 제한·사용자 재시도 기준은 바꾸지 않는다.

## 6. 범위 밖

전체 0~100% 추정, 예상 완료 시간, 대기 순번, 분석 알고리즘 변경, 다중 워커·메시지 브로커,
SSE 연결 유지용 heartbeat 주기. SSE heartbeat와 Python 생존 확인은 서로 다른 것이다.
