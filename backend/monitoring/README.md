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
```

prod 는 `environment="prod"`, 컨테이너 이름은 `sssok-app`·`sssok-nginx` 다.

### 요청 하나 추적하기

1. dev API 를 호출하고 응답 헤더의 `X-Request-ID` 를 복사한다.
2. Grafana → Explore → 데이터 소스 `Loki` 에서 `{environment="dev"} |= "<ID>"` 로 조회한다.
3. `service="sssok-nginx"` 줄에서 상태 코드·`durationSeconds`·`upstreamStatus` 를 본다.
4. `service="sssok-backend"` 줄에서 `errorCode`·`durationMs`·예외를 본다. 앱 줄이 없고 ID 가
   32자 hex 면 요청이 앱까지 가지 못한 것이다.
5. 같은 시간대를 데이터 소스 `Prometheus` 나 `sssOK Backend Overview` 대시보드에서 요청량으로 확인한다.

### dev 호스트 Nginx 설정

배포 파이프라인이 옮기지 않으므로 dev EC2 에서 한 번만 한다. 기존 설정과 `access.log` 는 건드리지 않고
JSON 접근 로그 파일을 하나 더 남긴다.

```bash
sudo cp monitoring/nginx/sssok-log-format.conf /etc/nginx/conf.d/sssok-log-format.conf
# dev-api 의 server 블록에 access_log 가 따로 있으면 그 블록에도
#   access_log /var/log/nginx/sssok-access.log sssok_json;
# 를 추가한다 (server 블록에 access_log 가 있으면 http 블록 설정을 물려받지 않는다).
sudo nginx -t && sudo systemctl reload nginx

curl -s -o /dev/null http://dev-api.ssssok.com/health
sudo tail -n 1 /var/log/nginx/sssok-access.log   # JSON 한 줄이 보이면 된다
```

필드는 `backend/nginx.conf`(prod)와 같다. 한쪽 필드를 바꾸면 다른 쪽도 같이 고친다.
되돌릴 때는 `/etc/nginx/conf.d/sssok-log-format.conf` 와 추가한 `access_log` 줄을 지우고 reload 한다.

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
