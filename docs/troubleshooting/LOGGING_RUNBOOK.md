# 로깅 Runbook

장애가 났을 때 로그로 원인을 찾는 순서를 정리한다. 수집 구조·라벨·질의 문법은
[monitoring/README.md](../../backend/monitoring/README.md#로그-수집과-조회)에 있으므로 여기서는 반복하지 않는다.

## 0. 접속

| 환경 | Grafana | 서버 셸 |
| --- | --- | --- |
| prod | <https://monitor.ssssok.com> | prod EC2 |
| dev | SSH 터널을 연 뒤 <http://localhost:3000> (`ssh -L 3000:localhost:3000 <dev EC2>`) | dev EC2 |

컨테이너 이름은 dev 가 `sssok-app-dev`·`sssok-alloy-dev`·`sssok-loki-dev`·`sssok-grafana-dev`, prod 는 같은
이름에서 `-dev` 를 뺀 것이다. 아래 명령은 dev 기준이다.

로그는 Grafana → Dashboards → sssOK → **sssOK Logs** 에서 본다. `environment` 는 그 Grafana 가 보는 환경이
자동으로 선택된다.

| 패널 | 언제 보나 |
| --- | --- |
| 최근 ERROR | 무엇이 터졌는지 처음 훑어볼 때 |
| Request ID 추적 | 사용자·프론트가 준 `X-Request-ID` 하나를 따라갈 때 |
| HTTP 500 응답 (앱) | 앱이 5xx 를 돌려준 요청 목록이 필요할 때 |
| 배포 버전별 오류 | 배포 직후 오류가 새 버전에서 나는지 확인할 때 |
| Nginx 4xx·5xx 응답 | 앱까지 가지 못한 요청(502·504)이나 4xx 폭증을 볼 때 |
| 애플리케이션 예외 | 예외 클래스·스택트레이스가 필요할 때 |

## 1. 오류 로그 찾기

1. 알림에서 왔다면 Discord 메시지의 **ERROR 로그 보기** 를 누른다. 알림 시작 15분 전부터 지금까지의
   sssOK Logs 가 열린다.
2. 대시보드에서 왔다면 sssOK Backend Overview 의 **5xx 응답 비율** 그래프를 눌러
   **이 시간대의 ERROR 로그 보기** 를 고른다. 대시보드와 같은 시간 범위로 열린다.
3. **최근 ERROR** 패널에서 줄을 펼쳐 `service` 를 본다.
   - `sssok-backend` 면 앱 예외다. 2단계(Request ID)로 간다.
   - `sssok-nginx` 이고 JSON 이면 Nginx 가 5xx 를 돌려준 요청이다. `upstreamStatus` 가 비었거나 502·504 면
     앱이 응답하지 못한 것이다. 앱이 살아 있는지부터 본다 (`docker ps`, Overview 의 서비스 상태).
   - JSON 이 아닌 `[error] ... connect() failed` 줄은 Nginx 에러 로그다. 같은 시각 앱이 내려가 있었다는 뜻이다.

## 2. Request ID 로 Nginx → 앱 로그 잇기

1. 응답 헤더 `X-Request-ID` 값을 받는다. 오류 로그에서 시작했다면 그 줄의 `requestId` 를 복사한다.
2. sssOK Logs 위쪽 `requestId` 칸에 붙여 넣는다. **Request ID 추적** 패널에 시간 순으로 나온다.
3. 줄을 이렇게 읽는다.

| 보이는 줄 | 뜻 |
| --- | --- |
| Nginx 줄 + 앱 접근 로그 줄 (+ 앱 ERROR 줄) | 앱까지 들어온 요청이다. 앱 줄의 `status`·`errorCode`·`durationMs` 를 본다 |
| Nginx 줄만 있고 ID 가 32자 hex (하이픈 없음) | 앱이 응답하지 못한 요청이다. Nginx 가 직접 ID 를 만들었다. `upstreamStatus` 와 그 시각의 앱 상태를 본다 |
| Nginx 줄만 있고 ID 가 UUID | 앱은 응답했지만 앱 로그가 빠졌다. 4장(로그가 들어오지 않을 때)을 본다 |
| 아무 줄도 없음 | 시간 범위를 넓힌다. 그래도 없으면 4장을 본다 |

앱 접근 로그는 `/health`·`/actuator` 를 남기지 않는다 (5xx 는 남긴다).

## 3. 500 원인을 예외 로그에서 찾기

1. **HTTP 500 응답 (앱)** 에서 해당 요청 줄의 `requestId` 를 복사해 2장 방법으로 같은 요청의 줄을 모은다.
2. 같은 ID 의 `level=ERROR` 줄을 펼쳐 `exception`(예외 클래스)과 `stackTrace` 를 본다. 스택트레이스는
   맨 아래 `Caused by:` 가 실제 원인인 경우가 많다.
3. 같은 예외가 여러 요청에서 나는지 보려면 **애플리케이션 예외** 패널에서 같은 `exception` 이 반복되는지 본다.
4. 배포 직후라면 **배포 버전별 오류** 의 `version` 칸에 새 `backendVersion`(또는 `gitSha` 앞부분)을 넣어
   새 버전에서만 나는지 확인한다. 비워 두면 줄마다 버전 필드가 붙어 있어 섞여 있는지 바로 보인다.
5. 원인이 DB 쪽(`DataAccessResourceFailureException`, `SQLTransientConnectionException`, `HikariPool ... Connection is not available`)이면
   Overview 의 HikariCP 커넥션 그래프로 pending 이 쌓였는지 같이 본다.

## 4. 로그가 들어오지 않을 때

앞 단계부터 하나씩 확인한다. 막힌 지점에서 멈추고 5장으로 간다.

1. **앱이 로그를 내는가** — 서버에서 `docker logs --since 5m sssok-app-dev | tail`.
   여기도 비어 있으면 로깅 문제가 아니라 앱 문제다. JSON 이 아니라 평문이면 프로파일(`SPRING_PROFILES_ACTIVE`)을 확인한다.
   dev Nginx 는 `sudo tail /var/log/nginx/sssok-access.log` 로 본다. 파일이 없으면
   [dev 호스트 Nginx 설정](../../backend/monitoring/README.md#dev-호스트-nginx-설정)이 빠진 것이다.
2. **Alloy 파이프라인이 도는가** — Alloy 의 `:12345` 는 호스트에 열려 있지 않으므로 같은 네트워크에 있는
   Grafana 컨테이너에서 부른다.

   ```bash
   docker exec sssok-grafana-dev curl -s http://alloy:12345/-/ready
   # 구성 요소별 상태. 모두 healthy 여야 한다.
   docker exec sssok-grafana-dev curl -s http://alloy:12345/api/v0/web/components | grep -o '"localID":"[^"]*"\|"state":"[^"]*"'
   docker logs --since 10m sssok-alloy-dev | grep -i -E 'error|warn' | tail
   ```

   응답이 없거나 컨테이너가 없으면 Alloy 가 멈춘 것이다 (`docker ps -a | grep alloy`).
   `loki.source.docker` 오류가 보이면 `docker-socket-proxy` 가 떠 있는지 본다.
3. **Loki 가 받는가**

   ```bash
   docker exec sssok-grafana-dev curl -s http://loki:3100/ready   # ready 여야 한다
   docker logs --since 10m sssok-loki-dev | grep -i -E 'error|level=warn' | tail
   ```

   `Ingester not ready: waiting for 15s after being ready`(503)는 기동 직후뿐 아니라 운영 중에도 잠깐씩 나올 수 있다
   (로컬 장애 테스트에서 몇 분간 503 이었다가 스스로 200 으로 돌아왔다). 1~2분 간격으로 다시 보고, 503 이어도
   Grafana 에서 최근 로그가 조회되면 수집은 되고 있는 것이다. 계속 503 이면서 최근 로그도 없을 때만 장애로 본다.
   `rate limit`·`ingestion rate` 가 보이면 수집량 상한에 걸린 것이다 (README 의 디스크와 수집량 참고).
   디스크가 찼는지 `df -h /` 도 본다.
4. **Grafana 가 Loki 를 보는가** — Grafana → Connections → Data sources → Loki → **Save & test**.
   실패하면 Grafana 와 Loki 가 같은 네트워크에 있는지, 3단계가 통과했는지 다시 본다.

Prometheus 가 Alloy·Loki 상태를 따로 감시하므로, 위 단계에서 막혔다면 `LogPipelineDown`·`LogShippingFailing`·
`LogEntriesDropped` 알림이 함께 왔는지 확인한다 (README 의 수집 경로 알림).

## 5. Alloy·Loki 재시작

둘 다 앱과 분리돼 있어 재시작해도 서비스에는 영향이 없다. 재시작하는 동안의 로그는 서버 로컬
(`docker logs`, `/var/log/nginx`)에 남아 있다가 Alloy 가 다시 뜨면 이어서 보내진다.

```bash
docker restart sssok-alloy-dev
docker restart sssok-loki-dev
# 컨테이너가 아예 없거나 설정을 바꿨다면 배포 디렉터리에서 다시 띄운다.
docker compose --env-file .env --env-file image.env -f docker-compose.dev.yml up -d alloy loki
```

재시작 뒤 4장 2·3단계로 정상인지 보고, sssOK Logs 에서 최근 줄이 다시 들어오는지 확인한다.
멈춘 사이 회전으로 밀려난 로그(json-file 30MB 초과분, 압축된 Nginx 로그)는 Loki 로 가지 않는다.

## 6. dev 에서 장애를 안전하게 일으키고 되돌리기

**prod 에서는 하지 않는다.** dev 와 prod 는 같은 RDS 인스턴스를 쓴다 (DB 이름만 다르다). 그래서 RDS 자체나
RDS 보안 그룹은 절대 건드리지 않고, dev EC2 안에서만 막는다.

### 앱 중단 (Nginx 502)

```bash
docker stop sssok-app-dev
curl -si http://dev-api.ssssok.com/health | head -1   # 502
# 되돌리기
docker start sssok-app-dev
```

`BackendDown` 알림은 2분 뒤 발화하고, 복구 뒤 resolved 가 온다.

### DB 연결 차단 (500, DB 오류 로그)

dev EC2 의 Docker 컨테이너에서 나가는 5432 연결만 거절한다. prod EC2 와 RDS 는 영향이 없다.

```bash
sudo iptables -I DOCKER-USER -p tcp --dport 5432 -j REJECT --reject-with tcp-reset
# DB 를 읽는 API 를 몇 번 호출하면 커넥션 획득 대기(`connection-timeout` 3초) 뒤 500 이 난다.

# 되돌리기 (반드시 실행한다)
sudo iptables -D DOCKER-USER -p tcp --dport 5432 -j REJECT --reject-with tcp-reset
sudo iptables -L DOCKER-USER -n   # 추가한 REJECT 줄이 없어야 한다
```

차단 중에는 Loki 에서 `{environment="dev", service="sssok-backend"} |~ "(?i)hikari|connection"` 로 DB 오류
로그를 본다. 상태 확인은 관리 포트로 한다.

```bash
docker exec sssok-app-dev wget -qO- http://localhost:8081/actuator/health/liveness
docker exec sssok-app-dev wget -qO- http://localhost:8081/actuator/health/readiness
```

DB 가 끊기면 readiness 는 `DOWN`(503), liveness 는 `UP`(200)이어야 한다. readiness 그룹에만 `db` 를 넣어 뒀다
(`application.yml` 의 `management.endpoint.health.group.readiness`). 컨테이너 healthcheck 는 `/health` 를 보므로
DB 가 끊겨도 앱 컨테이너는 재시작되지 않는다.

### 로그 수집 중단

```bash
docker stop sssok-alloy-dev
# 앱은 정상이고 docker logs·/var/log/nginx 에는 계속 쌓인다.
docker start sssok-alloy-dev
# 중단 중 로그까지 Loki 에 들어오는지 sssOK Logs 에서 그 시간대를 본다.
```

`LogPipelineDown` 은 5분 뒤 발화한다.

## 7. 민감정보 점검

마스킹이나 Nginx 로그 필드를 바꾼 뒤에는 Grafana Explore(데이터 소스 Loki)에서 아래가 모두 0건인지 본다.
기간은 점검 요청을 보낸 시간대로 잡는다.

```logql
{environment="dev"} |~ "eyJ[A-Za-z0-9_-]{4,}\\.[A-Za-z0-9_-]{4,}\\."   # JWT 원문
{environment="dev"} |~ "(?i)x-amz-signature=[^*&]"                   # R2 서명 값
{environment="dev"} |~ "(?i)[?&]token=[^*&]"                          # SSE 토큰 쿼리스트링
{environment="dev"} |= "jdbc:"                                        # DB URL
{environment="dev"} |~ "(?i)cookie"                                   # Cookie 헤더
{environment="dev"} |= "<관리자 비밀번호 원문>"                          # 검색만 하고 질의 기록은 지운다
```

서버 디스크도 본다. dev 호스트 Nginx 는 기본 `access.log`(combined)를 끄고 쿼리스트링 없는 JSON 로그만
남기도록 설정한다([dev 호스트 Nginx 설정](../../backend/monitoring/README.md#dev-호스트-nginx-설정)).
기본 `access.log` 는 요청 줄을 쿼리스트링째 적어 SSE 토큰이 평문으로 남기 때문이다.

```bash
sudo ls /var/log/nginx/                           # access.log* 가 없어야 한다
sudo grep -c '[?&]token=' /var/log/nginx/sssok-access.log   # 0
```

`access.log` 가 다시 생겼다면 Nginx 설정이 되돌아간 것이다. 위 문서 순서대로 기본 `access_log` 줄을 다시
끄고 reload 한 뒤, 생긴 `access.log*` 를 지운다. `error.log` 원본에는 요청 줄의 토큰이 남을 수 있지만
(Nginx 가 형식을 바꿀 수 없다) Loki 로 갈 때는 Alloy 가 가리고, 원본은 logrotate 보관 기간(Ubuntu 기본 14일)이
지나면 지워진다.
