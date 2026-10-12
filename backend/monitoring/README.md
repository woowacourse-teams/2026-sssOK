# 로컬 모니터링 실행

백엔드는 IDE 또는 Gradle로 실행하고 Prometheus와 Grafana만 Docker Compose로 실행한다.

```bash
# PostgreSQL
docker compose up -d

# Spring Boot
./gradlew bootRun

# 별도 터미널에서 Prometheus와 Grafana
docker compose -f docker-compose.monitoring.yml up -d
```

확인 주소:

- Actuator: <http://localhost:8081/actuator/health>
- Prometheus: <http://localhost:9090>
- Grafana: <http://localhost:3000> (`admin` / `admin`)

Grafana 비밀번호를 바꾸려면 실행 전에 `GRAFANA_ADMIN_PASSWORD` 환경변수를 설정한다. 이 구성은
로컬 대시보드와 Prometheus 알림 상태 확인용이며 Discord 알림은 전송하지 않는다.

서버 배포에서는 Actuator 관리 포트 `8081`을 호스트에 공개하지 않는다. Prometheus는 같은
Docker Compose 네트워크에서 `app:8081`로만 접근한다.

종료할 때는 데이터 볼륨을 유지할지 선택한다.

```bash
# 컨테이너만 종료
docker compose -f docker-compose.monitoring.yml down

# 컨테이너와 로컬 모니터링 데이터까지 삭제
docker compose -f docker-compose.monitoring.yml down -v
```

## 로그 수집과 조회

dev·prod 서버에서는 앱과 Nginx 로그를 Loki 로 모아 Grafana Explore 에서 조회한다.

```text
app 컨테이너(dev·prod), nginx 컨테이너(prod) 표준 출력
  → Docker json-file (max-size 10m × 3, 서버 로컬에 그대로 남는다) → Docker API ┐
dev 호스트 Nginx (/var/log/nginx, 호스트 logrotate 가 회전)          → 파일 읽기 ┤
                                                                               → Alloy (monitoring/alloy/config.alloy)
                                                                               → Loki (monitoring/loki/loki.yml, loki-data 볼륨)
                                                                               → Grafana Explore (데이터 소스 Loki)
```

- 컨테이너는 compose 에서 `com.sssok.log.service` 라벨을 붙인 것만 수집한다 (app, prod nginx).
- dev 의 Nginx 는 컨테이너가 아니라 EC2 에 apt 로 설치돼 `:80 → :8080` 으로 앱에 넘긴다. dev compose 가
  `/var/log/nginx` 를 Alloy 에 읽기 전용으로 마운트하고, Alloy 는 `sssok-access.log`(JSON)와 `error.log` 만
  읽는다. JSON 접근 로그는 아래 [dev 호스트 Nginx 설정](#dev-호스트-nginx-설정)을 한 번 해 둬야 생긴다.

### 라벨

Loki 스트림 라벨은 네 개만 쓴다.

| 라벨 | 값 | 출처 |
| --- | --- | --- |
| `service` | `sssok-backend`, `sssok-nginx` | 컨테이너 라벨 `com.sssok.log.service`, 호스트 Nginx 는 Alloy 설정 |
| `environment` | `dev`, `prod`, `local` | Alloy 의 `SSSOK_ENVIRONMENT` |
| `container` | `sssok-app-dev`, `nginx-host`(dev Nginx), `sssok-app`, `sssok-nginx` | 컨테이너 이름, 호스트 Nginx 는 고정값 |
| `level` | `DEBUG` `INFO` `WARN` `ERROR` | 로그 본문. Nginx 접근 로그는 상태 코드로 정한다 (5xx `ERROR`, 4xx `WARN`) |

`requestId`·`roomId`·`memberId`·`mediaId`·예외 메시지·전체 URL 은 라벨로 만들지 않는다. 요청마다
값이 달라서 라벨로 쓰면 스트림이 요청 수만큼 생긴다. 본문에 남아 있으므로 질의에서 거른다.

### 자주 쓰는 질의

```logql
# dev 환경의 ERROR 로그 (앱 예외 + Nginx 5xx·에러 로그)
{environment="dev", level="ERROR"}

# 특정 요청 하나를 Nginx·앱 로그에서 함께 (응답 헤더 X-Request-ID 값)
{environment="dev"} |= "<X-Request-ID>"

# 앱 / Nginx 로그만
{environment="dev", container="sssok-app-dev"}
{environment="dev", service="sssok-nginx"}

# 특정 배포 버전의 오류 (backendVersion 또는 gitSha)
{environment="dev", service="sssok-backend", level="ERROR"} | json | backendVersion="3.1.1"
{environment="dev", service="sssok-backend", level="ERROR"} | json | gitSha=~"a1b2c3d.*"

# 앱이 502·504 로 응답하지 못한 요청
{environment="dev", service="sssok-nginx"} | json | status >= 502

# 앱이 5xx 로 응답한 요청 (앱 접근 로그, 요청당 한 줄. 예외 줄에도 status 가 있어 durationMs 로 거른다)
{environment="dev", service="sssok-backend"} | json | __error__="" | status >= 500 | durationMs != ""

# 예외가 담긴 앱 로그 (exception·stackTrace 필드)
{environment="dev", service="sssok-backend"} | json | __error__="" | exception != ""
```

prod 는 `environment="prod"`, 컨테이너 이름은 `sssok-app`·`sssok-nginx` 다.

`| json` 뒤의 `__error__=""` 는 JSON 이 아닌 줄(Nginx 에러 로그, JVM 기동 실패 출력)을 뺀다. 빼지 않으면 숫자
비교에 실패한 줄이 걸러지지 않고 그대로 섞여 나온다.

위 질의와 Request ID·배포 버전 검색은 Grafana 의 **sssOK Logs** 대시보드(`grafana/dashboards/logs.json`)에
패널로 들어 있다. `sssOK Backend Overview` 의 5xx 응답 비율 그래프와 Discord 알림 메시지에서 이 대시보드로
바로 이동한다. 장애 상황별 대응 순서는 [로깅 Runbook](../../docs/troubleshooting/LOGGING_RUNBOOK.md)을 본다.

### 요청 하나 추적하기

1. dev API 를 호출하고 응답 헤더의 `X-Request-ID` 를 복사한다.
2. Grafana → Explore → 데이터 소스 `Loki` 에서 `{environment="dev"} |= "<ID>"` 로 조회한다.
3. `service="sssok-nginx"` 줄에서 상태 코드·`durationSeconds`·`upstreamStatus` 를 본다.
4. `service="sssok-backend"` 줄에서 `errorCode`·`durationMs`·예외를 본다. 앱 줄이 없고 ID 가
   32자 hex 면 요청이 앱까지 가지 못한 것이다.
5. 같은 시간대를 데이터 소스 `Prometheus` 나 `sssOK Backend Overview` 대시보드에서 요청량으로 확인한다.

### dev 호스트 Nginx 설정

배포 파이프라인이 옮기지 않으므로 dev EC2 에서 한 번만 한다. 접근 로그를 쿼리스트링 없는 JSON
파일(`sssok-access.log`) 하나로 바꾼다.

기본 `access.log`(combined)는 요청 줄 `$request` 를 그대로 적어 SSE 의 `?token=<JWT>` 같은 쿼리스트링이
서버 디스크에 평문으로 쌓인다. Loki 로 보내지 않아도 디스크에 남는 것 자체가 문제라 이 파일은 끈다.
JSON 형식은 `$uri`(쿼리스트링 제외)만 적는다.

```bash
sudo cp monitoring/nginx/sssok-log-format.conf /etc/nginx/conf.d/sssok-log-format.conf

# 1. 기본 access.log 를 끈다. /etc/nginx/nginx.conf 의 http 블록에서
#      access_log /var/log/nginx/access.log;
#    줄을 주석 처리한다. `access_log off;` 로 바꾸면 안 된다 — 같은 http 블록의 conf.d 에 있는
#    sssok-access.log 까지 같이 꺼진다.
# 2. server 블록에 따로 적힌 access_log 가 있는지 본다.
sudo grep -rn 'access_log' /etc/nginx/nginx.conf /etc/nginx/sites-enabled/ /etc/nginx/conf.d/
#    dev-api 의 server 블록에 access_log 가 있으면 그 줄을
#      access_log /var/log/nginx/sssok-access.log sssok_json;
#    으로 바꾼다 (server 블록에 access_log 가 있으면 http 블록 설정을 물려받지 않는다).
sudo nginx -t && sudo systemctl reload nginx

curl -s -o /dev/null 'http://dev-api.ssssok.com/health?token=check'
sudo tail -n 1 /var/log/nginx/sssok-access.log   # JSON 한 줄, path 에 ?token= 이 없으면 된다
sudo grep -c 'token=check' /var/log/nginx/access.log   # 0 이어야 한다 — 기본 access.log 가 꺼졌다

# 3. 이미 쌓인 기본 access.log 와 회전본에는 토큰이 남아 있으므로 지운다.
#    reload 뒤에는 Nginx 가 이 파일을 더 쓰지 않는다. 위 grep 이 0 인 걸 먼저 확인하고 지운다.
sudo rm -f /var/log/nginx/access.log /var/log/nginx/access.log.*
sudo grep -rlE '[?&]token=' /var/log/nginx/   # error.log* 외에는 나오지 않아야 한다
```

`sssok-access.log` 는 Ubuntu 기본 logrotate(`/etc/logrotate.d/nginx`)가 회전한다. 기본값은 일 단위 14개 보관이고,
서버 값은 `cat /etc/logrotate.d/nginx` 로 확인한다.
`error.log` 는 요청 줄을 쿼리스트링째 남기는 Nginx 동작을 바꿀 수 없다. Loki 로 보낼 때는 Alloy 가
토큰 값을 가리고(`config.alloy` 의 `stage.replace`), 디스크 원본은 위 logrotate 보관 기간이 지나면 지워진다.

필드는 `backend/nginx.conf`(prod)와 같다. 한쪽 필드를 바꾸면 다른 쪽도 같이 고친다.
prod 는 컨테이너 Nginx 의 server 블록에 `access_log /dev/stdout sssok_json;` 만 두어 combined 형식이 남지 않는다.
되돌릴 때는 `/etc/nginx/conf.d/sssok-log-format.conf` 를 지우고 주석 처리한 `access_log` 줄을 되살린 뒤 reload 한다.

### 보존과 장애 시 동작

- 보존 기간: dev 7일, prod 30일 (`LOKI_RETENTION_PERIOD`). Loki compactor 가 지난 로그를 지운다.
- Loki·Alloy 가 멈춰도 앱과 Nginx 는 영향을 받지 않는다. 컨테이너 로그는 json-file 에, dev Nginx 로그는
  `/var/log/nginx` 에 계속 쌓인다. Alloy 가 다시 뜨면 저장해 둔 위치부터 이어 보낸다. 멈춘 사이 회전으로
  밀려난 로그(json-file 30MB 초과분, logrotate 로 압축된 Nginx 로그)는 Loki 로 가지 않는다.
- Loki 데이터는 `loki-data` 볼륨에 있어 컨테이너를 재생성해도 남는다. `down -v` 는 이 볼륨까지 지운다.
- Loki 는 `mem_limit` 512MB, Alloy 는 256MB 로 묶어 앱 메모리를 잠식하지 않게 한다.

### 디스크와 수집량

Loki 는 용량 기준 보존이 없다. 보존 기간이 지나기 전에 디스크를 채우지 않도록 수집량 상한을 둔다.

- 상한: 초당 1MB, 순간 4MB (`loki.yml` 의 `ingestion_rate_mb`). 넘는 로그는 거절되고 `LokiRejectingLogs` 알림이 뜬다.
- 추정: 요청 하나에 앱·Nginx 로그가 약 1KB, 저장 시 압축으로 1/10 안팎 (로컬 측정: 87MB 수신 → 6MB 저장).
  요청 하루 10만 건이면 하루 약 10MB, prod 30일 보존이면 약 300MB 다.
- 확인: 서버에서 `df -h /` 와 `docker system df -v | grep loki-data` 로 남은 디스크와 Loki 사용량을 본다.
- 디스크 알림: node_exporter 가 루트 디스크 사용량을 넘기고 `prometheus/rules/host-alerts.yml` 이 알린다.
  여유 15% 미만 `HostDiskSpaceLow`, 5% 미만 `HostDiskSpaceCritical`, 최근 6시간 추세로 하루 안에 찰 것 같으면
  `HostDiskWillFillIn24h`. Loki 만이 아니라 이미지·컨테이너 로그가 채우는 경우도 같이 잡는다.

### 수집 경로 알림

Prometheus 가 Alloy(`alloy:12345`)·Loki(`loki:3100`) 메트릭도 수집하고,
`prometheus/rules/logging-alerts.yml` 의 규칙으로 Discord 에 알린다.

| 알림 | 조건 |
| --- | --- |
| `LogPipelineDown` | Alloy 또는 Loki 가 5분 이상 응답 없음 |
| `LogShippingFailing` | Alloy → Loki 전송이 10분 넘게 실패 |
| `LogEntriesDropped` | 재시도를 다 쓰고 버린 로그가 생김 |
| `LokiRejectingLogs` | Loki 가 수집량 상한 등으로 로그를 거절함 (로그 폭주 신호) |

### Docker API 접근

Alloy 는 `docker.sock` 을 직접 마운트하지 않고 `docker-socket-proxy` 를 거친다. 소켓은 `:ro` 로 마운트해도
API 권한(컨테이너 생성·exec)은 그대로라서다. 프록시는 컨테이너·네트워크 조회(GET)만 허용하고 나머지는
403 으로 막는다. 프록시는 Alloy 만 붙는 내부 네트워크(`docker-api`)에 있어 앱·Nginx·Grafana 는 닿지 않는다.
컨테이너 조회 응답에는 환경변수가 들어 있다는 점은 남는 노출이다.

### 로컬에서 확인하기

로컬 앱(IDE·Gradle)은 호스트 프로세스라 수집되지 않는다. 위 `docker-compose.monitoring.yml` 로 Loki·Alloy·
Grafana 를 띄우면 `com.sssok.log.service` 라벨이 붙은 컨테이너의 로그만 `environment="local"` 로 모인다.
수집 경로 전체를 확인하려면 앱 이미지를 빌드해 `docker-compose.dev.yml` 을 별도 프로젝트 이름
(`docker compose -p <이름> ...`)과 로컬용 `.env`·`image.env` 로 띄운다.

- Loki: <http://localhost:3100/ready>
- Alloy 파이프라인 상태: <http://localhost:12345>

## 업로드·다운로드 대시보드 (#441)

Grafana의 **sssOK → sssOK Upload & Download** (`/d/sssok-media-transfer`)에서 확인한다.
기존 Backend Overview 상단에도 링크가 있다. `grafana/dashboards/media-transfer.json`은 기존
프로비저닝 경로로 자동 로드된다. **새 앱 배포 후부터** 추가 지표가 쌓이며 과거 값은 복원되지 않는다.

확인 순서:

1. **업로드**: URL 발급·완료 등록의 파일별 접수/거절 → API 상태 코드·p95.
   HTTP 200/201 안에서도 파일별 실패가 있어 API 오류율만으로 판단하지 않는다.
2. **미디어 처리**: 사진·영상 처리량 → 정상 생성 비율 → 결과별 건수 → 평균/p95.
   `retryable`이 늘면 회수 결과, 제출 거부와 실행기 큐를 함께 본다.
3. **회수**: 마지막 배치 발견 수와 정상 실행 시각을 같이 본다. 발견 수는 한 배치에서 선택한
   개수(현재 최대 50)이며 DB 전체 적체 수가 아니다. 빈 배치도 발견 수 0·실행 시각을 갱신한다.
4. **다운로드**: API 요청·응답 → ZIP 준비 성공/실패 → 실행 중·대기/처리 시간.
   ZIP 상태 조회 폴링은 다운로드 API 요청 수에 포함된다.
5. **실행기**: 공용 풀(사진·ZIP·정리 등)과 영상 전용 풀의 큐·활성 스레드·남은 용량을 비교한다.
   큐에는 아직 제출되지 못한 DB 작업이 포함되지 않는다.

상단 `집계 구간`은 5분/15분/1시간 이동 구간이다. 작은 트래픽에서도 추세를 볼 수 있게 기본 15분을
쓴다. 건수는 Prometheus `increase`의 추정값이므로 소수가 나타날 수 있다. 결과 0건은 성공률 100%로
바꾸지 않는다. 수집이 끊긴 경우와 구분하려면 `백엔드 메트릭 수집 상태`도 확인한다.

| Prometheus 메트릭 | 라벨 | 의미 |
| --- | --- | --- |
| `sssok_upload_files_total` | `stage=issue/register`, `result=accepted/rejected` | API 응답의 파일 항목 수. 재요청도 다시 집계 |
| `sssok_media_processing_seconds_count/sum/bucket` | `kind=image/video`, `source=initial/sweeper`, `result` | 한 번의 미디어 처리 시도 횟수·누적 시간·분포 |
| `sssok_media_submissions_total` | `kind`, `source`, `result=accepted/rejected` | 실행기 제출 결과. 접수와 처리 성공은 다름 |
| `sssok_media_sweep_found` | 없음 | 마지막으로 완료된 배치가 선택한 항목 수 |
| `sssok_media_sweep_last_success_seconds` | 없음 | 마지막 배치 완료 Unix 시각. 최초 실행 전 0 |
| `sssok_download_processing_seconds_count/sum/bucket` | `result=success/failure` | ZIP 워커 실행 결과와 시간 |
| `sssok_download_waiting_seconds_count/sum/bucket` | 없음 | 잡 생성부터 워커의 RUNNING 전환까지. 실제 시작한 잡만 표본 |
| `sssok_download_active` | 없음 | 이 프로세스에서 실행 중인 ZIP 워커 수 |

미디어 `result`: `success`(정상 파생본 생성), `retryable`(재처리 필요), `permanent_failure`(읽지 못하는
이미지), `completed_without_thumbnail`(영상 썸네일 없이 완료), `skipped`(삭제됐거나 이미 처리된 작업).
처리 결과는 **실행 시도 기준**이며 중복 실행·동시 삭제 상황의 고유 파일 상태 전이 수는 아니다.
성공률 분모에는 `skipped`를 제외한다. 평균·p95 패널은 빠른 실패가 정상 처리 시간을 낮춰 보이지
않도록 `success`만 조회한다. 결과별 모든 시간은 동일 메트릭의 `result` 필터로 조회할 수 있다.

Timer로 횟수와 시간을 함께 수집하고, histogram 경계는 미디어 0.1~120초, ZIP 0.1~1800초로 제한했다.
경계는 경보 임계값이 아니다. 경계 사이 p95는 보간값이고 최상위 유한 경계를 넘는 값이 많으면
p95가 실제보다 낮게 보일 수 있으므로 분포를 보고 경계를 조정한다. `mediaId`·`jobId`·파일명·예외
메시지는 라벨에 넣지 않는다. 개별 실패는 기존 로그의 `mediaId`·`jobId`로 찾는다.

**관측 경계**: R2 직접 PUT/GET, 사용자 기기 저장 완료는 백엔드에서 볼 수 없다. ZIP 성공은 ZIP
업로드와 READY 반영을 마친 것이지 사용자가 파일을 받은 것이 아니다. 워커의 실행 시간에는 큐·회수
대기가 포함되지 않는다. JVM 재시작으로 남은 DB `PROCESSING`/`QUEUED`/`RUNNING`의 전체 수나
가장 오래된 항목은 이 화면에서 직접 집계하지 않는다. 경보와 운영 임계값은 별도 작업이다.

공식 문서와 구현 연결:

- [Micrometer Timer](https://docs.micrometer.io/micrometer/reference/concepts/timers.html): 처리 결과가 정해진
  후 `Timer.Sample`을 종료해 횟수와 시간을 같은 결과 라벨로 기록한다.
- [Prometheus 계측](https://prometheus.io/docs/practices/instrumentation/): 비동기 처리·실행기·배치 상태를
  구분하고, 마지막 성공 시각과 제한된 라벨을 사용한다.
- [Spring Boot 3.5 Metrics](https://docs.spring.io/spring-boot/3.5/reference/actuator/metrics.html): HTTP와
  실행기의 자동 계측을 재사용한다.

조회식 회귀 테스트(저장소 루트에서 실행):

```bash
docker run --rm --entrypoint /bin/promtool \
  -v "$PWD/backend/monitoring/tests:/tests:ro" \
  prom/prometheus:v3.14.0 test rules /tests/media-transfer.test.yml
```

테스트는 실제 `/api/v1` 경로의 업로드 POST만 선택하는지, 건너뜀을 제외한 처리 시도 성공률과 ZIP
완료 건수가 맞는지 검증한다. 대시보드의 해당 조회식을 바꾸면 테스트 식도 함께 갱신한다.
