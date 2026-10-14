# 부하 시험 가이드 (k6 + 시드·정리 도구)

> 처음 보는 사람이 이 문서만 읽고 **시드 준비 → 5명 사전 점검 → 30명 목록 측정 → 결과 확인 → 정리** 를 끝낼 수 있게 썼다.
> 전체 계획(90·150·300명, 검색·업로드·다운로드)은 #517, 이 도구를 만든 작업은 #520 이다.
> 방 생성·입장·열람·폴더 시나리오와 여러 방 시드는 #533 에서 추가했다 — [3-1. 주요 API 시나리오](#3-1-주요-api-시나리오-방-생성입장열람폴더).

## 0. 한눈에 보기

부하 시험은 "실제 사용자 30명이 동시에 갤러리를 열면 서버가 버티는가"를 사람 대신 프로그램으로 재현하는 일이다.
사람이 직접 30명을 모으지 않는다. 대신 아래 세 가지 도구가 순서대로 일한다.

```
① seed.mjs ──────────▶ ② k6 (media-list.js) ─────────▶ ③ cleanup.mjs
가짜 계정 30개, 방 1개,     가상 사용자 30명이 그 계정으로       ①에서 만든 사진·방만
사진 300장을 API 로 만들고    갤러리 목록 API 를 반복 호출하고     골라서 지운다
fixtures 파일에 기록         응답 시간·실패율을 results 에 저장
        │                          │
        └──── fixtures/<runId>.json ┘   ← 두 도구를 잇는 파일 (계정 토큰·방 ID·사진 ID)
```

사람이 하는 일은 **언제·어디에·얼마나 걸지 정하고, 도는 동안 Grafana 를 보며 위험하면 멈추고, 결과를 해석하는 것**이다.

| 단계 | 사람 | 도구가 자동으로 |
| --- | --- | --- |
| 준비 | 대상 서버·시간·AI 비용 한도 결정, 팀 공지 | 계정·방·사진 생성, 만든 즉시 fixtures 기록, 서버를 다시 조회해 개수 검증 |
| 사전 점검 | 결과가 정상인지 확인 | 5명으로 목록 흐름 실행, 업무 실패가 1건이라도 있으면 실패 종료 |
| 본 측정 | 시작, Grafana 관찰, 위험하면 Ctrl+C | 예열과 본 측정을 나눠 실행, API 실패율 5% 초과가 1분 이어지면 자동 중단 |
| 결과 확인 | 병목 해석 | API별 요청 수·p95·실패율·실제 요청률을 JSON 으로 저장 |
| 정리 | 남은 범위(계정) 처리 결정 | fixtures 에 기록된 사진·방만 API 로 삭제 |

## 1. 먼저 알아둘 말

| 말 | 뜻 |
| --- | --- |
| **시드(seed)** | 시험에 쓸 데이터를 미리 만들어 두는 것. 여기서는 계정·방·사진 |
| **fixtures** | 시드 결과를 적은 JSON 파일. 계정 토큰이 들어 있어 **절대 공유·커밋하지 않는다** |
| **VU (가상 사용자)** | k6 가 흉내 내는 사용자 한 명. VU 30 = 동시에 30명 |
| **smoke (사전 점검)** | 5명으로 짧게 돌려 "스크립트가 제대로 동작하는지"만 보는 실행. 성능 판단에 쓰지 않는다 |
| **warmup (예열)** | 서버가 막 뜬 직후의 느린 구간(JIT·캐시·커넥션)을 버리는 구간. 결과는 따로 집계하고 판단에 쓰지 않는다 |
| **main (본 측정)** | 판단에 쓰는 구간 |
| **p95** | 요청 100개를 빠른 순으로 줄 세웠을 때 95번째 응답 시간. "대부분의 사용자가 겪는 최악" |
| **HTTP 실패** | 응답 코드가 기대와 다름 (401·403·5xx 등) |
| **업무 실패** | 응답 코드는 200 인데 내용이 틀림 (사진 수가 다르거나 썸네일 주소가 없음). k6 기본 지표는 이걸 못 잡아서 따로 센다 |
| **phaseRate** | 그 구간에서 실제로 보낸 초당 요청 수. "목표"가 아니라 "실제로 도달한 값" |

## 2. 준비 (한 번만)

| 필요한 것 | 확인 명령 | 설치 |
| --- | --- | --- |
| Node.js 20 이상 | `node -v` | https://nodejs.org (외부 패키지 없음, `npm install` 불필요) |
| k6 | `k6 version` | macOS `brew install k6` / 그 외 https://grafana.com/docs/k6/latest/set-up/install-k6/ |
| Docker | `docker version` | 입장 시나리오(SSE)용 `bin/k6` 를 빌드할 때만 필요하다 |

모든 명령은 `backend/loadtest` 에서 실행한다.

```bash
cd backend/loadtest
```

도구 자체가 멀쩡한지 먼저 본다. `pass 12` 가 나오면 된다.

```bash
npm test
```

**입장 시나리오(`room-entry.js`)를 돌릴 사람만** SSE 확장이 든 k6 를 한 번 빌드한다.
k6 기본 기능으로는 SSE 연결을 유지할 수 없고, SSE 확장(xk6-sse)은 k6 의 자동 확장 받기 대상이 아니라서
`import sse from 'k6/x/sse'` 를 그냥 `k6` 로 돌리면 `unknown dependency : k6/x/sse` 로 멈춘다.

```bash
npm run build:k6
```

Docker 의 `grafana/xk6` 이미지로 `bin/k6` 를 만든다(Go 설치 불필요, 첫 빌드 1~2분). `Extensions: ... k6/x/sse` 가 보이면 된다.
`bin/` 은 Git 에서 제외된다. 부하 발생용 EC2(리눅스)에 올릴 때는 `GOOS=linux GOARCH=amd64 npm run build:k6` 로 만든다.
`bin/k6` 는 일반 k6 기능을 모두 갖고 있어서 다른 시나리오도 그대로 돌릴 수 있다.

## 3. 단계별 실행

아래는 로컬 서버(`http://localhost:8080`) 기준이다. dev 에 걸 때는 [6. dev 에서 실행할 때](#6-dev-에서-실행할-때)를 먼저 읽는다.

### ① 시드 준비

```bash
node seed/seed.mjs --base-url http://localhost:8080
```

**안에서 일어나는 일** (전부 실제 API 호출, DB 직접 수정 없음)

1. `GET /version` 으로 대상 서버가 살아 있는지 확인하고 `fixtures/<runId>.json` 을 만든다
2. `POST /api/v1/auth/anonymous` 로 익명 계정 30개 생성 (닉네임 `lt<4자리>r1u0` ~ `r1u29`)
3. 첫 계정(방장)이 `POST /api/v1/rooms` 로 방 생성
4. 나머지 29명이 `POST /api/v1/rooms/{roomId}/members` 로 입장
5. 검색 API 를 한 번 호출해 **AI 분석이 켜진 서버인지** 확인 (켜져 있으면 여기서 멈춘다 — 아래 참고)
6. 사진 300장 업로드. 30명이 10장씩 나눠 올린다. 한 묶음(10장)마다
   - `POST /media/upload-urls` 로 업로드 주소 발급 → **바로 fixtures 에 기록**
   - 발급받은 주소로 스토리지(R2)에 파일 직접 PUT
   - `POST /media` 로 완료 등록 (서버가 스토리지에 실물이 있는지 확인)
7. 썸네일이 모두 READY 가 될 때까지 5초마다 확인
8. 서버를 다시 조회해 계정 수·입장 여부·사진 수·썸네일 상태가 맞는지 검증 → 맞으면 `COMPLETED`

**정상 출력**

```
[07:25:17] 검색 API 503 — 분석 꺼짐으로 판단
[07:25:19] 방 1/1: 업로드 50/300
...
시드 COMPLETED (runId=20261014T0733Z-6420)
  roomId=5 계정 30개(미입장 0) 사진 300장(목록 300, 미준비 0)

다음 단계 — 아래 줄을 그대로 복사해 실행한다
  export FIXTURES=fixtures/20261014T0733Z-6420.json
  k6 run -e PROFILE=smoke k6/scenarios/media-list.js
  node seed/cleanup.mjs --dry-run
```

**마지막 `export FIXTURES=...` 줄을 그대로 복사해 실행한다.** 이후 명령은 이 값을 자동으로 쓴다
(터미널을 새로 열면 다시 실행해야 한다).

| 옵션 | 기본 | 설명 |
| --- | --- | --- |
| `--rooms` | 1 | 방 수. 방마다 `--members` 명씩 만든다 (90명 시험 = 3) |
| `--members` | 30 | 방당 계정 수 (방장 포함) |
| `--photos` | 300 | 방당 사진 수. 정식 열람 측정은 1500 |
| `--entrants` | 0 | 방당 신규 입장 시험용 계정 수. 계정만 만들고 방에는 넣지 않는다. `--resume` 과 함께 쓰면 이미 입장한 계정을 빼고 이 수만큼 채운다 |
| `--images-dir` | - | 실제 사진 폴더. 생략하면 1280x960 PNG 를 장마다 다르게 만든다 |
| `--concurrency` | 3 | 동시에 올리는 10장 묶음 수 |
| `--ready-timeout` | 600 | 썸네일 준비를 기다리는 초 |
| `--resume <file>` | - | 실패한 시드를 이어서 준비 |
| `--allow-ai-cost` | - | AI 분석이 켜진 서버에도 올린다 (사진마다 외부 AI 호출) |
| `--yes` | - | 로컬이 아닌 서버에 실행할 때 필수 |

**중간에 실패하면** 실패 메시지 끝에 이어서 하는 명령과 정리 명령이 같이 나온다. 만든 자원은 생기는 즉시 기록되므로 버려지지 않는다.

```bash
node seed/seed.mjs --base-url http://localhost:8080 --resume fixtures/<runId>.json
```

**실제 사진을 쓰려면** (업로드·ZIP 시험처럼 원본 크기가 중요할 때) `images/` 폴더에 JPEG·PNG 를 넣고 `--images-dir images` 를 붙인다.
이미지가 아닌 파일이나 20MB 넘는 파일이 하나라도 있으면 시작 전에 멈춘다. `images/` 는 Git 에서 제외된다.

### ② 사전 점검 (5명)

```bash
k6 run -e PROFILE=smoke k6/scenarios/media-list.js
```

5명이 각자 갤러리를 3번 연다(총 15번). **성능을 보는 단계가 아니다.** "스크립트·토큰·응답 검증이 맞는가"만 본다.

**정상 출력** — 업무 실패 0%, 마지막에 `판정 기준 통과`

```
-- smoke: iterations 15, dropped 0
   media_all          요청     15       15 req/s  p95   254.66ms  HTTP 실패 0.00%  업무 실패 0.00%
판정 기준 통과
```

`판정 기준 미달` 이 나오면 본 측정으로 넘어가지 않는다. 콘솔에 `[media_all] ... 실패: status 401` 처럼 원인이 찍힌다.

### ③ 본 측정 (30명)

```bash
k6 run -e PROFILE=load -e VUS=30 -e WARMUP=2m -e DURATION=3m -e RUN_ID=list-30-$(date -u +%Y%m%dT%H%MZ) -e SCRIPT_VERSION=$(git rev-parse --short HEAD) k6/scenarios/media-list.js
```

30명이 동시에 "갤러리 열기 → 1~3초 둘러보기 → 다시 열기"를 반복한다. 처음 2분은 예열, 다음 3분이 본 측정이다.
**도는 5분 동안 Grafana 를 띄워 둔다** ([5. Grafana 에서 확인하기](#5-grafana-에서-확인하기)).

**정상 출력**

```
-- warmup: iterations 76, dropped 0
   media_all          요청     76     15.2 req/s  p95   880.75ms  HTTP 실패 0.00%  업무 실패 0.00%
-- main: iterations 414, dropped 0
   media_all          요청    414     13.8 req/s  p95  1393.07ms  HTTP 실패 0.00%  업무 실패 0.00%
판정 기준 통과
결과 파일: results/list-30-20261014T0740Z-media-list.json
```

**판단은 `main` 줄로 한다.** 위 예시라면 "30명이 동시에 쓰면 갤러리 열기가 초당 13.8번 일어나고, 95%는 1.39초 안에 열린다"로 읽는다.

자주 바꾸는 값:

| 변수 | 기본 | 설명 |
| --- | --- | --- |
| `PROFILE` | smoke | `smoke` 사전 점검 / `load` 본 측정 |
| `VUS` | smoke 5 · load 30 | 동시 사용자 수. 각자 fixtures 계정 하나를 고정해서 쓴다 |
| `WARMUP` / `DURATION` | 2m / 3m | 예열·본 측정 시간 |
| `RUN_ID` | 시작 시각 | 결과 파일 이름. 무엇을 돌렸는지 알아보게 붙인다 |
| `SCRIPT_VERSION` | unknown | 결과에 남길 커밋. 위 명령처럼 `$(git rev-parse --short HEAD)` |
| `RESULTS_DIR` | results | 결과 저장 폴더. 없는 폴더면 시작 전에 멈춘다 (끝나고 저장하다 결과를 잃지 않도록) |
| `THINK_TIME` | 2 | 다시 열기 전 평균 대기 초 (0.5~1.5배 무작위) |
| `MODE` | vus | `rate` 로 바꾸면 사용자 수 대신 요청률(`RATE`=초당 횟수)을 고정한다. 데이터 크기만 바꿔 비교할 때 쓴다 |
| `LIST_API` | all | `paged` 로 바꾸면 앱이 쓰지 않는 커서 API(`/media?size=`)를 비교용으로 잰다 |
| `BASE_URL` | fixtures 의 서버 | fixtures 와 다르면 시작하지 않는다 (다른 서버 토큰으로 요청하는 사고 방지) |

### ④ 결과 확인

`results/` 에 두 파일이 생긴다.

| 파일 | 용도 |
| --- | --- |
| `<RUN_ID>-media-list.json` | 사람이 보는 요약 |
| `<RUN_ID>-media-list.k6.json` | k6 원본 (나중에 다시 분석할 때) |

```bash
cat results/<RUN_ID>-media-list.json
```

| 항목 | 읽는 법 |
| --- | --- |
| `startedAtUtc` / `finishedAtUtc` | **UTC** 시각. 한국 시간은 +9시간. Grafana 에서 이 구간을 찾는다 |
| `appVersion` | 측정한 서버 버전. 개선 전후 비교 때 달라야 하는 유일한 값 |
| `data` | 몇 명·몇 장으로 시드했는지. 비교할 두 결과는 같아야 한다 |
| `config` | k6 설정(VU·시간·모드). 비교할 두 결과는 같아야 한다 |
| `phases.main.apis.media_all.p95Ms` | 본 측정 p95 (ms) |
| `...phaseRate` | 실제 도달한 초당 요청 수 |
| `...httpFailRate` / `businessFailRate` | 0.01 = 1% |
| `phases.main.droppedIterations` | `MODE=rate` 에서 목표만큼 못 보낸 횟수. 0 이 아니면 목표 요청률에 못 미친 것 |
| `thresholds.passed` | 자동 중단·판정 기준을 통과했는지 |

k6 의 p95 는 **내 컴퓨터에서 잰 값**이라 네트워크 왕복이 포함된다. 서버 안에서 걸린 시간은 Grafana 에서 본다. 둘 차이가 크면 네트워크나 응답 크기 문제다.

### ⑤ 정리

```bash
node seed/cleanup.mjs --dry-run
```

지울 대상만 보여준다. 확인했으면 실제로 지운다 (로컬이 아니면 `--yes` 추가).

```bash
node seed/cleanup.mjs
```

```
정리 결과
  roomId=5 사진 삭제 300장(이미 없음 0) 방 삭제
  API 로 정리하지 못한 범위: 계정 30개, 미완료 예약 0건 (fixtures.cleanup.leftovers)
```

- **fixtures 에 적힌 사진·방만 지운다.** 그 방에 다른 사람이 올린 사진이 있어도 건드리지 않고, 방장이 fixtures 와 다르면 그 방은 건너뛴다.
- **사진을 먼저 지우고 방을 지운다.** 방만 지우면 스토리지 원본이 남기 때문이다. 사진 삭제가 원본·썸네일·프리뷰 정리를 예약한다.
- **익명 계정은 지울 API 가 없어 DB 에 남는다.** 신규 입장용 계정을 포함한 계정 ID 목록은 fixtures 의 `cleanup.leftovers.accountUserIds` 에 있다.
- 정리한 fixtures 는 `CLEANED` 가 되어 다시 쓸 수 없다. 다시 돌리면 "이미 정리됨"만 출력한다.

## 3-1. 주요 API 시나리오 (방 생성·입장·열람·폴더)

> 30명 목록 측정(위 ①~⑤)을 끝낸 뒤 진행한다. **하나의 dev 에 서로 다른 부하 시험을 동시에 돌리지 않는다.**
> 결과 확인·정리·Grafana 는 위와 같다. 결과 파일 이름은 `<RUN_ID>-<시나리오>.json` 이다.

### 규모 맞추기 — 방당 30명

30·90·150·300명은 **방 수로** 맞춘다. 방마다 30명(방장 포함)이 들어가 있고, 가상 사용자는 fixtures 계정에 하나씩 붙는다.

| 규모 | 시드 | 열람 `VUS` |
| --- | --- | --- |
| 30명 | `--rooms 1` | 30 |
| 90명 | `--rooms 3` | 90 |
| 150명 | `--rooms 5` | 150 |
| 300명 | `--rooms 10` | 300 |

신규 입장도 같이 볼 거면 `--entrants 30` 을 붙인다. 방마다 아직 들어가지 않은 계정 30개가 따로 생긴다 (300명 조건이면 300개).

```bash
node seed/seed.mjs --base-url http://localhost:8080 --rooms 3 --photos 1500 --entrants 30
```

시드 시간은 사진 수에 비례한다. 방 10개 × 1,500장은 15,000장이므로 dev 에서는 실행 시간과 썸네일 처리량을 먼저 확인한다.

### 방 생성 — `room-create.js`

방장이 방을 새로 만들고, 공유 코드로 확인하고, 빈 갤러리를 여는 흐름이다. 만든 방은 그 자리에서 지운다.

```
반복 하나 = 방 하나 (방장은 시드 계정을 돌려 쓴다)
  POST   /rooms                 생성 — 201, 방장이 나, ACTIVE, 방장이 참여자로 등록, 사진 0
  GET    /rooms/{code}          공유 코드로 조회 — 만든 방과 같은지
  GET    /media/all             빈 갤러리 — 0장
  DELETE /rooms/{roomId}        삭제
  GET    /rooms/{code}          삭제 확인 — status DELETED
```

```bash
k6 run -e PROFILE=smoke k6/scenarios/room-create.js
```

```bash
k6 run -e PROFILE=load -e RATE=1 -e WARMUP=1m -e DURATION=3m -e RUN_ID=create-1rps-$(date -u +%Y%m%dT%H%MZ) -e SCRIPT_VERSION=$(git rev-parse --short HEAD) k6/scenarios/room-create.js
```

| 변수 | 기본 | 설명 |
| --- | --- | --- |
| `RATE` | 1 | 초당 만들 방 수. 사용자 수가 아니라 요청률을 고정한다 |
| `VUS` | RATE × 5 (최소 5) | 요청률을 맞추려고 쓸 수 있는 가상 사용자 상한. 모자라면 `dropped iterations` 에 남는다 |
| `WARMUP` / `DURATION` | 1m / 3m | 예열·본 측정 시간 |

- **방 삭제는 soft delete 다.** 지워도 방 행은 DB 에 `DELETED` 로 남는다. 초당 1개로 4분이면 240행이다.
  방 이름이 `lt<시드 4자리>c...` 라 DB 에서 골라낼 수 있다 (예: `select status, count(*) from room where name like 'ltb8d3c%' group by status`).
- 새 계정을 만들지 않고 시드 계정을 방장으로 돌려 쓴다. 계정이 쌓이지 않는다.
- 중간에 실패한 방은 한 번 더 지운다(`room_cleanup`, 결과 집계 제외). 그래도 남으면 콘솔에 `지우지 못한 방 roomId=...` 를 남기고 `rooms_left` 로 센다. **`rooms_left` 가 0 이 아니면 그 roomId 를 방장 토큰으로 직접 지운다.**

### 입장 — `room-entry.js` (SSE, `bin/k6` 필요)

처음 초대 링크를 연 사람이 들어와 갤러리를 보고 실시간 연결을 유지하는 흐름이다.

```
신규 입장자 1명 (= 미입장 계정 1개, 한 번만)
  GET  /rooms/{code}             초대 링크로 방 확인 — 아직 미입장이어야 한다
  POST /rooms/{roomId}/members   입장 — 201(새로 입장)만 성공. 200(이미 입장)은 신규 입장으로 세지 않는다
  GET  /media/all                최초 갤러리 목록 — 사진 수·썸네일 검증
  GET  /rooms/{roomId}/events    SSE 구독 — 마지막 입장 뒤 OBSERVE 동안 끊기지 않아야 성공
```

```bash
./bin/k6 run -e PROFILE=smoke k6/scenarios/room-entry.js
```

```bash
./bin/k6 run -e PROFILE=load -e RUN_ID=entry-90-$(date -u +%Y%m%dT%H%MZ) -e SCRIPT_VERSION=$(git rev-parse --short HEAD) k6/scenarios/room-entry.js
```

| 변수 | 기본 | 설명 |
| --- | --- | --- |
| `ENTRANTS` | smoke 5 · load fixtures 의 미입장 계정 전부 | 들어올 인원. 방을 번갈아 섞어서 들어온다 |
| `ENTRY_WINDOW` | smoke 10s · load 60s | 이 시간 동안 고르게 나눠 입장시킨다 |
| `OBSERVE` | smoke 30s · load 5m | 입장 구간이 끝난 뒤 연결을 유지하며 관찰하는 시간 |

**정상 출력** — API 3개와 `sse_ok` 가 모두 성공

```
-- entry: iterations 5, dropped 0
   room_lookup          요청      5      0.5 req/s  p95    38.64ms  HTTP 실패 0.00%  업무 실패 0.00%
   room_join            요청      5      0.5 req/s  p95    24.99ms  HTTP 실패 0.00%  업무 실패 0.00%
   media_all_initial    요청      5      0.5 req/s  p95    38.97ms  HTTP 실패 0.00%  업무 실패 0.00%
   sse_ok{phase:entry}            rate=1 passes=5 fails=0
   sse_open_ms{phase:entry}       ... max=1993 ...
   sse_events{phase:entry}        count=10 rate=0.1875
판정 기준 통과
```

**SSE 결과 읽는 법**

| 지표 | 뜻 |
| --- | --- |
| `sse_ok` | 연결이 열리고 관찰 끝까지 유지된 비율. 403·연결 실패·중간 끊김·첫 데이터 지연은 모두 실패다 |
| `sse_open_ms` | 구독 요청부터 **첫 데이터**까지 걸린 시간. 서버가 첫 이벤트나 heartbeat(15초 주기)를 보낼 때까지 응답을 내보내지 않아서 **최대 약 15초**가 정상이다. 연결 수립 시간으로 읽지 않는다. 20초를 넘으면 실패로 센다 |
| `sse_events` | 받은 이벤트 수(heartbeat 제외). 먼저 들어온 사람은 뒤에 들어온 사람의 `room.member.joined` 를 받는다 |

SSE 연결 시간은 요청이 아니라 연결 유지 시간이라 API p95 에 넣지 않는다. 관찰 종료 뒤 첫 heartbeat 에서 닫으므로 실행은 `ENTRY_WINDOW + OBSERVE` 보다 최대 15초 더 걸린다.

**한 번 쓴 계정은 다시 쓸 수 없다.** 입장한 계정은 서버에 입장 상태로 남는다. 같은 fixtures 로 다시 돌리면 setup 이 이를 감지해 요청 전에 멈춘다.
다시 돌리기 전에 미입장 계정을 채운다 (방마다 99개까지).

```bash
node seed/seed.mjs --base-url http://localhost:8080 --resume $FIXTURES --entrants 30
```

### 열람 — `gallery-browse.js`

이미 방에 있는 사람들이 갤러리를 열고, 사진을 눌러 보고, 가끔 좋아요를 누르는 흐름이다. #517 의 "열람 30 → 90 → 150 → 300명"은 이 시나리오로 잰다.

```
가상 사용자 1명 (= fixtures 계정 1개) 반복
  GET /media/all                      갤러리 열기
  DETAILS 번:
    GET /media/{id}                   사진 상세 — 썸네일이 준비된 사진 중 무작위
    LIKE_RATIO 확률로 PUT /likes → DELETE /likes
  THINK_TIME 쉼
```

```bash
k6 run -e PROFILE=smoke k6/scenarios/gallery-browse.js
```

```bash
k6 run -e PROFILE=load -e VUS=90 -e WARMUP=2m -e DURATION=5m -e RUN_ID=browse-90-$(date -u +%Y%m%dT%H%MZ) -e SCRIPT_VERSION=$(git rev-parse --short HEAD) k6/scenarios/gallery-browse.js
```

| 변수 | 기본 | 설명 |
| --- | --- | --- |
| `VUS` | smoke 5 · load fixtures 계정 전부 | 동시 사용자 수. 위 규모 표대로 방 수 × 30 으로 맞춘다 |
| `WARMUP` / `DURATION` | 2m / 5m | #517 기준: 30명 3~5분, 90·150명 5분, 300명 10~15분 |
| `DETAILS` | 3 | 한 번 둘러볼 때 여는 상세 수 |
| `LIKE_RATIO` | 0.3 | 상세에서 좋아요를 누를 확률. smoke 에서는 경로 확인을 위해 항상 누른다 |
| `THINK_TIME` | 2 | 쉬는 시간(초, 0.5~1.5배 무작위). 상세 사이는 이 값의 절반 |

- 좋아요는 눌렀다가 바로 취소한다. 취소가 실패하면 한 번 더 취소한다(`unlike_cleanup`, 결과 집계 제외).
  보통은 끝나도 좋아요 수가 시드 때와 같지만, **서버 오류가 이어지거나 중간에 멈추면 좋아요가 남을 수 있다.**
  검증은 좋아요 수에 기대지 않으므로 같은 fixtures 로 다시 돌려도 되지만, 좋아요 수까지 같아야 하는 개선 전후 비교라면 시드를 새로 한다.
- 같은 `/media/all` 이라도 이 시나리오에서는 `media_all` 로, 입장 시나리오에서는 `media_all_initial` 로 따로 집계된다.

### 폴더 — `folder-ops.js`

방마다 대표 사용자(기본 방장 1명)가 폴더를 만들고 사진을 정리하는 흐름이다.

```
방별 대표 사용자 반복
  POST   /folders               폴더 생성
  PATCH  /folders/{id}          이름 변경
  PUT    /media/folders         사진 FOLDER_PHOTOS 장 담기 — 담긴 수·폴더 사진 수 검증
  GET    /media/all?folderId=   폴더 열기 — 담은 사진만 정확히 보여야 한다
  DELETE /media/folders         꺼내기
  DELETE /folders/{id}          폴더 삭제
  THINK_TIME 쉼
```

```bash
k6 run -e PROFILE=smoke k6/scenarios/folder-ops.js
```

```bash
k6 run -e PROFILE=load -e WARMUP=1m -e DURATION=5m -e RUN_ID=folder-3rooms-$(date -u +%Y%m%dT%H%MZ) -e SCRIPT_VERSION=$(git rev-parse --short HEAD) k6/scenarios/folder-ops.js
```

| 변수 | 기본 | 설명 |
| --- | --- | --- |
| `USERS_PER_ROOM` | 1 | 방마다 폴더를 정리할 인원. 방장부터 차례로 쓴다 |
| `FOLDER_PHOTOS` | 10 | 한 번에 담고 꺼낼 사진 수 |
| `WARMUP` / `DURATION` | 1m / 5m | 예열·본 측정 시간 |
| `THINK_TIME` | 3 | 한 바퀴 뒤 쉬는 시간(초) |

- 만든 폴더는 그 바퀴 안에서 지운다. 중간에 실패하면 한 번 더 지운다(`folder_cleanup`, 결과 집계 제외). 그래도 남은 폴더는 정리 도구가 방을 지울 때 함께 사라진다.
- 폴더 생성·이름 변경·담기는 그 방 구독자 모두에게 SSE 이벤트로 퍼진다.

## 4. 시나리오는 어디에 무엇이 있나

```
loadtest/
├── seed/
│   ├── seed.mjs             ① 시드 — 위 "안에서 일어나는 일" 순서 그대로
│   ├── cleanup.mjs          ⑤ 정리
│   └── lib/
│       ├── api.mjs          시드용 API 호출 (재시도 포함)
│       ├── fixtures.mjs     fixtures 형식·저장 (권한 600, 원자적 저장)
│       ├── images.mjs       PNG 생성, 실제 사진 형식·해상도 검사
│       └── cli.mjs          옵션 파싱, 로컬 아닌 서버 차단
├── k6/
│   ├── scenarios/                       ← 사용자가 "무엇을 하는지"
│   │   ├── media-list.js    ②③ 갤러리 목록 (목록만, 데이터 크기 비교)
│   │   ├── room-create.js   3-1 방 생성·조회·삭제
│   │   ├── room-entry.js    3-1 신규 입장 + SSE 유지 (bin/k6)
│   │   ├── gallery-browse.js 3-1 열람: 목록·상세·좋아요
│   │   └── folder-ops.js    3-1 폴더 생성·이름 변경·담기·꺼내기
│   └── lib/
│       ├── config.js        fixtures·환경 변수 검증 (잘못되면 요청 전에 멈춤)
│       ├── http.js          인증 헤더, 응답 파싱, 업무 성공 판정 (모든 시나리오 공통)
│       ├── run.js           예열·본 측정 구간, 중단 기준, 시작 전 대상·사진 수 확인
│       ├── sse.js           방 이벤트 구독·유지 판정 (xk6-sse)
│       └── summary.js       결과 JSON·콘솔 요약 생성
├── scripts/build-k6.sh      xk6-sse 를 넣은 bin/k6 빌드 (npm run build:k6)
├── bin/                     빌드한 k6 — Git 제외
├── test/                    도구 단위 테스트 (npm test)
├── fixtures/                시드 결과 — Git 제외
├── results/                 측정 결과 — Git 제외
└── images/                  실제 사진 — Git 제외
```

### `k6/scenarios/media-list.js` 가 하는 일

실제 앱은 방에 들어가면 `GET /api/v1/rooms/{roomId}/media/all` 로 **사진 전체를 한 번에** 받는다
(`frontend/src/entities/media/api/getPhotos.ts`). 시나리오는 이 동작을 그대로 흉내 낸다.

```
가상 사용자 1명 (= fixtures 계정 1개)
  반복:
    GET /media/all  ──▶  응답 검증
    1~3초 쉼 (둘러보는 시간)
```

**응답 검증** — 아래가 전부 맞아야 성공으로 센다. 하나라도 틀리면 "업무 실패".

- 응답 코드 200
- `items` 개수가 시드한 사진 수(300)와 같다 → 빈 목록·다른 방 응답을 성공으로 세지 않는다
- 모든 항목에 `mediaId`·`thumbnailUrl` 이 있다 → 썸네일이 안 보이는 응답을 성공으로 세지 않는다

**시작 전 확인 (`setup`)** — 부하를 걸기 전에 아래가 틀리면 요청을 한 건도 보내지 않고 멈춘다.

- fixtures 상태가 `COMPLETED` 이고 토큰이 만료되지 않았다
- `BASE_URL` 이 fixtures 의 서버와 같다
- 서버의 현재 사진 수가 fixtures 와 같다 (그 방에 누가 사진을 올리거나 지웠으면 측정 조건이 달라진다)

**자동 중단** — 본 측정 구간에서 HTTP 실패율 5% 초과 또는 업무 성공률 95% 미만이 1분 이어지면 k6 가 스스로 멈춘다.
DB·서버 메모리·운영 영향은 k6 가 볼 수 없으므로 사람이 Grafana 를 보고 멈춘다.

다른 시나리오(검색·업로드·다운로드)를 추가할 때는 `k6/scenarios/` 에 파일을 만들고 `lib/` 모듈을 그대로 쓴다.
SSE 를 쓰는 시나리오는 `lib/sse.js` 를 쓰고, 일반 API 요청과 **다른 가상 사용자**에서 연결을 연다. `sse.open` 은 연결이 끝날 때까지 그 가상 사용자를 붙잡는다.

## 5. Grafana 에서 확인하기

> **로컬 서버로 돌린 시험은 Grafana 에 나오지 않는다.** Grafana 는 dev·prod 서버만 수집한다.
> 로컬에서는 콘솔·결과 파일만 보고, Grafana 확인은 dev 에 걸 때 한다.

주소: https://monitor.ssssok.com/central (팀 계정으로 로그인)

### 시간 범위 맞추기

결과 파일의 `startedAtUtc` 에 **+9시간** 하면 한국 시간이다. 예열 2분 뒤부터 3분이 본 측정 구간이다.
Grafana 오른쪽 위 시간 선택에서 그 구간을 직접 입력한다. 시험 중이라면 "Last 15 minutes" + 자동 새로고침 10s 로 둔다.

### 띄워 둘 화면 4개

| 화면 | 설정 | 보는 것 |
| --- | --- | --- |
| **sssOK Central Backend Overview** | 상단 `environment` = **dev** | 시험 대상 서버 상태 |
| **sssOK Central Backend Overview** (새 탭) | `environment` = **prod** | **운영이 영향을 받는지.** dev 와 운영이 같은 DB(RDS)를 쓴다 |
| **sssOK Host Overview** | `environment` = **dev** | dev 서버 CPU·메모리·Swap |
| **AWS 콘솔 → RDS → 모니터링** | sssok RDS 인스턴스 | DB CPU·연결 수. **Grafana 에는 DB 지표가 없다** |

### Backend Overview 패널 읽는 법

| 패널 | 정상 | 이상 신호 |
| --- | --- | --- |
| 요청량 | 시험 시작과 함께 k6 의 req/s 만큼 오른다 | 안 오르면 k6 가 다른 서버를 치고 있다 |
| 5xx 응답 비율 | 0% | 조금이라도 생기면 로그 확인 |
| p95 응답 시간 | k6 p95 보다 조금 낮다 (네트워크 제외) | 계속 오르기만 하면 서버가 밀리고 있다 |
| API별 p95 · 상위 5개 | `/api/v1/rooms/{roomId}/media/all` 이 보인다 | 다른 API 가 같이 느려지면 공용 자원(DB·스레드) 문제 |
| JVM Heap 사용률 | 오르내림 반복 (GC) | 계속 높게 붙어 있으면 메모리 부족 |
| HikariCP · 연결 풀 사용 / 대기 | **pending 0** | pending 이 1 이상으로 이어지면 DB 연결이 모자람 (max 10) |
| DB 연결 획득 · 평균 대기 시간 | 수 ms | 100ms 넘게 이어지면 DB 병목 |
| GC 평균 정지 시간 | 수십 ms 이하 | 길어지면 응답이 멈칫한다 |

CPU 는 Backend Overview 에 없다. **Host Overview 의 "CPU 비유휴 비율"** 로 본다. dev 서버에는 사실상 백엔드만 돌아서 서버 CPU 가 곧 앱 CPU 다.
85% 넘게 붙어 있으면 CPU 병목이다.

### 목록 API 만 따로 보기 (Explore)

왼쪽 메뉴 Explore → 데이터소스 Prometheus → Code 모드에 붙여 넣는다.

서버 안에서 잰 `/media/all` p95 (초):

```promql
histogram_quantile(0.95, sum by (le) (rate(http_server_requests_seconds_bucket{environment="dev",method="GET",uri="/api/v1/rooms/{roomId}/media/all"}[1m])))
```

`/media/all` 초당 요청 수 (k6 의 phaseRate 와 비슷해야 한다):

```promql
sum(rate(http_server_requests_seconds_count{environment="dev",method="GET",uri="/api/v1/rooms/{roomId}/media/all"}[1m]))
```

`uri` 의 `{roomId}` 는 실제 숫자로 바꾸지 않는다. 서버가 경로를 그 모양 그대로 기록한다.

### 바로 멈춰야 하는 순간 (Ctrl+C)

| 조건 | 어디서 |
| --- | --- |
| **prod** 화면의 p95 나 5xx 가 시험 전보다 눈에 띄게 오름 | Backend Overview (prod) |
| RDS CPU 80% 이상이 2분 이어짐 | AWS 콘솔 |
| HikariCP pending 1 이상 또는 DB 연결 획득 100ms 초과가 1분 이어짐 | Backend Overview (dev) |
| dev 가용 메모리 10% 미만, Swap 쓰기 증가, CPU 85% 초과 | Host Overview (dev) |
| 앱 재시작·OOM, 디스코드에 BackendDown·5xx 알림 | 알림 채널 |

멈춘 실행도 결과 파일은 남는다. 무엇 때문에 멈췄는지 실행 기록에 적는다.

## 6. dev 에서 실행할 때

| 항목 | 정한 값 |
| --- | --- |
| 대상 | `https://dev-api.ssssok.com` |
| 부하를 보내는 곳 | 30명까지는 팀원 노트북(유선 또는 안정적인 Wi-Fi). 150명 이상·업로드 시험은 dev 와 같은 리전의 별도 EC2. **dev 서버 안에서 k6 를 돌리지 않는다** (측정 대상의 CPU 를 같이 쓴다) |
| 시간 | **평일 14:00~17:00 KST**, 한 번에 30분 이내. 시작 10분 전 팀 채널에 공지 |
| 시험 중 금지 | `develop` 에 백엔드 머지 (dev 자동 배포로 서버가 재시작된다), 다른 부하 시험 동시 실행 |
| AI 비용 | 목록 시험은 dev 의 검색 분석(`IMAGE_SEARCH_ENABLED`)을 **끈 상태**에서 시드한다. 켜져 있으면 시드가 감지해 멈춘다. 검색 시험 때만 켜고, 하루 사진 분석 3,000장·검색 2,000회를 넘기지 않는다 |

**2026-10-14 기준 dev 는 검색 분석이 켜져 있다** (dev·prod 모두 `imageAnalysisTaskExecutor` 지표가 있다). 목록 시험 시드 전에 끄거나, 비용을 감수하고 `--allow-ai-cost` 를 붙인다.

dev 실행 명령은 로컬과 같고 `--base-url` 과 `--yes` 만 다르다.

```bash
node seed/seed.mjs --base-url https://dev-api.ssssok.com --yes
```

## 7. fixtures 형식

`seed/lib/fixtures.mjs` 가 정한다. 형식을 바꾸면 `schemaVersion` 을 올리고 `k6/lib/config.js` 를 함께 고친다.

```jsonc
{
  "schemaVersion": 2,               // v1 파일도 읽는다 (entrants 없이 v2 로 올린다)
  "runId": "20261014T0733Z-6420",
  "baseUrl": "http://localhost:8080",
  "status": "COMPLETED",            // IN_PROGRESS · COMPLETED · FAILED · CLEANED
  "condition": { "rooms": 1, "membersPerRoom": 30, "photosPerRoom": 300, "entrantsPerRoom": 30, "image": { } },
  "searchProbe": { "status": 503, "searchEnabled": false },
  "verification": { "passed": true, "problems": [], "rooms": [ ] },
  "rooms": [{
    "roomId": 5, "code": "XJG83HDB", "name": "lt6420-r1",
    "host":         { "userId": 31, "nickname": "lt6420r1u0", "accessToken": "…", "expiresAt": "…", "joined": true },
    "participants": [ { "userId": 32, "…": "…" } ],
    "entrants":     [ { "userId": 61, "nickname": "lt6420r1e1", "…": "…", "joined": false } ],   // 신규 입장 시험용, 방에 넣지 않음
    "photos": [ { "mediaId": 31, "fileName": "…", "uploaderUserId": 31, "mimeType": "image/png",
                  "size": 330026, "width": 1280, "height": 960, "status": "READY" } ]
  }],
  "cleanup": null
}
```

- **accessToken 이 들어 있다.** 파일 권한 600 으로 저장되고 Git 에서 제외된다. 결과를 공유할 때는 `results/*.json` 만 보낸다.
- 닉네임·방 이름이 `lt` 로 시작해서 DB 에서도 실험 데이터임을 알아볼 수 있다.
- 토큰 유효기간은 서버 설정(기본 30일)을 따른다. 만료되면 k6 가 시작하지 않으므로 시드를 다시 한다.

`git status` 에 `?? fixtures/`, `?? results/` 가 보이는 건 정상이다. 폴더를 남기려고 둔 `.gitkeep` 이 보이는 것이고, 시드 결과 JSON 은 제외되어 있다.
정말 제외됐는지는 아래로 확인한다. 각 파일 옆에 `.gitignore` 규칙이 나오면 된다.

```bash
git check-ignore -v fixtures/*.json results/*.json
```

## 8. 문제 해결

| 증상 | 원인 → 조치 |
| --- | --- |
| `zsh: no such file or directory: runId` | 문서의 `<runId>` 를 꺾쇠까지 그대로 쳤다. 시드 출력의 `export FIXTURES=...` 줄을 복사해 쓴다 |
| `--base-url 이 필요합니다` / `FIXTURES 가 필요합니다` | 새 터미널이라 `export FIXTURES=...` 가 사라졌다. 다시 실행한다 |
| `... 은 로컬이 아닙니다` | 로컬이 아닌 서버. 실행 조건을 팀과 확인했으면 `--yes` |
| `이미지 검색이 켜져 있어 ...` | 대상 서버의 AI 분석이 켜져 있다. 끄고 `--resume`, 또는 비용을 감수하고 `--allow-ai-cost --resume` |
| 시드가 `썸네일 준비 대기` 에서 오래 멈춤 | 서버의 썸네일 작업이 밀렸다. `--ready-timeout` 을 늘리거나 서버 로그 확인 후 `--resume` |
| `스토리지 PUT 403` | 업로드 주소 만료(10분) 또는 헤더 불일치. `--concurrency 1` 로 낮춰 `--resume` |
| `unknown dependency : k6/x/sse` | 입장 시나리오를 그냥 `k6` 로 돌렸다. `npm run build:k6` 후 `./bin/k6 run ...` |
| 입장 시나리오가 `이미 입장한 계정 ...개가 섞여 있습니다` 로 멈춤 | 이전 실행에서 쓴 계정이다. `node seed/seed.mjs --base-url <URL> --resume $FIXTURES --entrants 30` 으로 채운다 |
| `신규 입장용 미입장 계정이 없습니다` | 시드에 `--entrants` 를 주지 않았다. 위 명령으로 채운다 |
| `sse_ok` 실패 `관찰 종료 전 끊김` | 관찰 중 연결이 끊겼다. 서버 재시작·배포·Nginx 타임아웃 여부를 확인한다 |
| `sse_ok` 실패 `첫 데이터 ...ms` | 20초 안에 heartbeat 도 오지 않았다. 서버 스케줄러(heartbeat)가 밀리고 있는지 본다 |
| k6 가 `fixtures 상태가 FAILED` 로 시작 안 함 | 시드 검증 실패. fixtures 의 `verification.problems` 를 보고 `--resume` |
| k6 setup 에서 `사진 수가 fixtures 와 다릅니다` | 그 방에 다른 업로드·삭제가 섞였다. 시드를 새로 한다 |
| `businessFailRate` 만 높음 | 응답 코드는 정상인데 내용이 틀렸다. 콘솔의 `[media_all] ... 실패:` 로그로 원인 확인 |
| Grafana 에 아무것도 안 보임 | 로컬 서버로 돌렸다. Grafana 는 dev·prod 만 수집한다 |

## 부록. 도구를 고칠 때 로컬에서 검증하기

실제 R2 대신 로컬 MinIO(S3 호환 저장소)에 백엔드를 붙이면 dev 를 건드리지 않고 업로드까지 끝까지 돌려볼 수 있다.
`<키>`·`<비밀값>` 은 아무 값이나 정해서 세 곳에 똑같이 넣는다.

```bash
docker run -d --name loadtest-minio -p 127.0.0.1:59000:9000 -e MINIO_ROOT_USER=<키> -e MINIO_ROOT_PASSWORD=<비밀값> quay.io/minio/minio server /data
```

```bash
docker exec loadtest-minio sh -c 'mc alias set l http://localhost:9000 <키> <비밀값> && mc mb l/sssok-loadtest'
```

DB 는 pgvector 가 든 Postgres 여야 한다(마이그레이션 V28 이 `vector` 확장을 켠다). 개발용 DB 를 더럽히지 않도록 시험용 컨테이너를 따로 띄운다.

```bash
docker run -d --name loadtest-postgres -p 127.0.0.1:55432:5432 -e POSTGRES_USER=sssok -e POSTGRES_PASSWORD=sssok -e POSTGRES_DB=sssok pgvector/pgvector:0.8.6-pg16-bookworm
```

`backend` 디렉터리에서 서버를 띄운다. 디스코드 알림이 실제로 나가지 않도록 웹훅을 비우고, 검색 분석(외부 AI)을 끈다.
`backend/.env` 에 실제 R2·웹훅 값이 있어도 환경 변수가 우선한다.

```bash
DB_URL=jdbc:postgresql://localhost:55432/sssok CORS_ALLOWED_ORIGINS=http://localhost:5173 IMAGE_SEARCH_ENABLED=false R2_ENDPOINT=http://127.0.0.1:59000 R2_ACCESS_KEY=<키> R2_SECRET_KEY=<비밀값> R2_BUCKET=sssok-loadtest R2_PUBLIC_BASE_URL=http://127.0.0.1:59000/sssok-loadtest DISCORD_FEEDBACK_WEBHOOK_URL= ./gradlew bootRun
```

다 쓴 뒤에는 컨테이너를 지운다.

```bash
docker rm -f loadtest-minio loadtest-postgres
```
