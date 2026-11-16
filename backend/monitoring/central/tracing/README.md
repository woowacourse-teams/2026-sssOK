# HTTP 트레이스 시험 배치 (#835)

개발·운영 앱의 기본 HTTP 요청을 Tempo에 저장하고 기존 중앙 Grafana에서 조회한다.
업로드 내부 스팬과 DB 작업 큐의 추적 연결은 후속 작업이다. 이 변경만으로 이미지 변환 내부나 모든 SQL이 보이지 않는다.

## 구성과 기본값

- Spring Boot Micrometer Tracing → OTLP HTTP → 중앙 Nginx 내부 `13100/v1/traces` → Tempo.
- Grafana는 같은 Docker 네트워크의 `http://sssok-tempo:3200`으로 조회한다.
- 중앙의 기존 6개 서비스 배포·복구는 유지한다. Tempo는 별도 `sssok-tracing` Compose 프로젝트다.
- Tempo 3.1.0: 512MiB 제한, Swap 추가 사용 금지, CPU 2개, GOMEMLIMIT 384MiB, 조회 동시성/분할 2, 보관 24시간.
- 자동 재시작을 끈다. OOM으로 종료되면 원인 확인 후 사람이 다시 시작한다.
- 기존 `13100` 내부 포트만 사용한다. Nginx는 실제 연결 원본이 개발/운영 IP인 POST만 받는다.
  MCP 호스트·외부에는 허용하지 않는다. `3200`, `4318`은 호스트에 게시하지 않는다.
- 기본 수집은 꺼져 있다. 전송 큐 512개/배치 64개/주기 1초, 연결 1초/전송 2초로 제한한다.
  큐 초과나 전송 실패 시 트레이스가 유실될 수 있으며 업무 요청을 기다리게 하지 않는다.
- 원문 HTTP URL·쿼리는 스팬에서 제외한다. 메서드·라우트·상태는 유지한다.
  JSON 로그에 traceId/spanId를 추가하고 Loki → Tempo, Tempo → 환경별 Loki 연결을 제공한다.

## 로컬 검증

```bash
python3 backend/monitoring/central/tracing/smoke.py
cd backend
./gradlew integrationTestH2 --tests '*HttpTracingTest' --tests '*ActuatorEndpointTest'
./gradlew unitTest --tests '*SssOkJsonLogFormatterTest'
```

smoke.py는 별도 고유 네트워크·볼륨·로컬 랜덤 포트를 만들고 실제 OTLP 저장/trace ID 조회 후 시험 자원만 삭제한다.
HTTP 테스트는 실제 앱의 `/version` 요청, 민감한 쿼리 제외, 응답하지 않는 수집기와의 분리를 검증한다.
실제 로컬 Tempo의 4318/3200 포트를 열었다면 다음 환경변수로 앱 → Tempo까지 추가 검증할 수 있다.

```bash
TEMPO_SMOKE_ENDPOINT=http://127.0.0.1:14318 \
TEMPO_SMOKE_QUERY_ENDPOINT=http://127.0.0.1:13200 \
./gradlew integrationTestH2 --tests '*HttpTracingTest' --rerun-tasks
```

이 환경변수는 로컬 시험에만 사용하며 운영 주소를 넣지 않는다.

## 중앙 시험 배치 순서

1. 승인된 커밋을 중앙 설정 배포 workflow로 반영한다. Nginx 경로와 Grafana 데이터 소스만 반영되며 Tempo는 자동 기동되지 않는다.
   `tracing/`는 기존 deploy.py의 관리 파일이 아니므로 아래 별도 준비가 필요하다.
2. 같은 커밋의 `tracing/compose.yml`, `tracing/tempo.yml`을 `$HOME/monitoring/tracing/`에 복사한다.
   기존 `.env`, 중앙 볼륨·서비스·보안/플랫폼 에이전트는 변경하지 않는다.
3. 최신 `free -m`, `docker stats --no-stream`, 호스트 24시간 메모리·Swap·디스크를 기록한다.
   이미 가용 메모리 200MiB 미만이거나 기존 모니터링이 비정상이면 시작하지 않는다.
4. 중앙 기본 네트워크가 존재하는지 확인하고 다음 명령으로 Tempo만 시작한다.

```bash
cd "$HOME/monitoring/tracing"
docker network inspect sssok-monitoring_default >/dev/null
docker compose config
docker compose up -d
# 중앙 Grafana 컨테이너를 통해 내부 readiness 확인 (인증 정보 출력 없음)
docker exec sssok-monitoring-grafana-1 curl -fsS http://sssok-tempo:3200/ready
```

5. **개발 서버에만** 아래 `.env` 값을 추가하고 정상 배포 절차로 앱을 재생성한다.
   `TRACING_ENVIRONMENT`는 dev/prod Compose가 고정하며 로컬은 기본 local이다.

```dotenv
TRACING_ENABLED=true
TRACING_SAMPLING_PROBABILITY=1.0
TRACING_ENDPOINT=http://10.0.20.204:13100/v1/traces
```

6. 개발 `/version` 요청을 보내고 Grafana Explore의 Tempo에서
   `{ resource.service.name = "sssok-backend" && resource.deployment.environment = "dev" }`로 조회한다.
   최근 스팬 검색은 기본 검색 cutoff로 잠시 지연될 수 있다. 로그의 trace ID 상세 조회도 확인한다.
7. 개발 업로드 40/100/200장과 검색을 실행하면서 아래 중단 기준을 감시한다.
   현재 단계에서는 HTTP 등록 구간만 보이며 후처리 내부는 아직 계측되지 않았다.
8. 개발 검증을 통과한 뒤 운영은 같은 endpoint, `TRACING_ENABLED=true`, 수집률 `0.05`로 별도 배포한다.
   운영 메트릭은 전수 집계하지만 샘플링되지 않은 요청의 트레이스는 남지 않는다.
   W3C 부모의 샘플링 결정을 이어받는 요청은 단순 5%와 다를 수 있다.
9. 최소 하루 동안 데이터 누적·검색·기존 수집 상태를 관찰한 뒤 유지 여부를 결정한다.

## 관측과 중단

- 중앙 가용 메모리, Swap 입출력, CPU, 디스크와 Grafana/Prometheus/Loki 응답을 함께 본다.
- `docker stats --no-stream`으로 Tempo 사용량을 기록하고 `docker inspect`의 State.OOMKilled도 확인한다.
- 가용 메모리 **200MiB 미만이 1분 지속**, OOM 또는 기존 모니터링 장애 시 Tempo를 중지한다.
  이 1분 기준은 시험 담당자가 감시해야 하며 자동 중단 기능은 아니다.
- 앱의 전송 실패 경고·수집 누락·HTTP 응답 지연도 확인한다. 512MiB 상한은 자원 예약이나 운영 보장이 아니다.
- 장애 시 해당 Compose 폴더에서 `docker compose stop tempo`로 즉시 중지한다.
  앱은 전송 실패를 허용하므로 그 뒤 `TRACING_ENABLED=false`로 정상 재배포해 전송도 끈다.
- `down -v`와 볼륨 삭제는 하지 않는다. 재개할 때 기존 시험 데이터가 남아 있다.
- 중앙 설정을 이전 커밋으로 복구해도 별도 Tempo는 자동 중지되지 않으므로 먼저 위 중단 절차를 수행한다.

## 한계와 근거

중앙 최소 가용 메모리 약 355MiB, 로컬 합성 시험의 조정된 Tempo 피크 약 436MiB였다.
현재 사양에서 상시 운영 안전성은 입증되지 않았다. 저장소는 로컬 볼륨이며 단일 서버 장애·디스크 손실에 취약하다.
24시간 보관도 디스크 하드 상한은 아니므로 WAL/압축 중 공간을 관찰한다.
메트릭 생성 기능·추가 Collector는 이번에 활성화하지 않는다.

- [Spring Boot tracing](https://docs.spring.io/spring-boot/3.5/reference/actuator/tracing.html)
- [Tempo configuration](https://grafana.com/docs/tempo/latest/configuration/)
- [Grafana Loki derived fields](https://grafana.com/docs/grafana/latest/datasources/loki/configure/)
