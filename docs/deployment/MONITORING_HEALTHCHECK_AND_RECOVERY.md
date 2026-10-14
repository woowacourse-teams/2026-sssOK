# Compose healthcheck와 모니터링 자동 복구

Grafana가 Prometheus와 Loki를 안정적으로 사용하려면 컨테이너의 실행 여부뿐 아니라 실제 요청을 받을 준비가 됐는지 확인해야 한다. 동시에 readiness 실패가 복구 로직 자체를 건너뛰게 만들지 않도록 배포 스크립트의 실패 경로를 설계해야 한다.

## 컨테이너 실행과 서비스 준비 상태

컨테이너가 `running`이어도 프로세스 초기화, 설정 로딩, 저장소 복구가 끝나지 않았다면 요청에 응답하지 못할 수 있다.

- Compose의 기본 시작 조건인 `service_started`는 컨테이너가 시작됐는지만 확인한다.
- `healthcheck`는 컨테이너 내부 명령을 주기적으로 실행해 `starting`, `healthy`, `unhealthy` 상태를 만든다.
- `depends_on.condition: service_healthy`를 사용하면 의존 서비스의 healthcheck가 성공한 뒤에 다음 서비스를 시작한다.
- sssOK에서는 Prometheus의 `/-/ready`와 Loki의 자체 `-health` 명령으로 준비 상태를 확인하고, Grafana가 두 서비스의 `healthy` 상태를 기다리게 한다.
- Docker Compose의 시작 순서와 조건에 대한 기준은 [Docker 공식 startup order 문서](https://docs.docker.com/compose/how-tos/startup-order/)를 따른다.

## `service_healthy`가 배포 실패 경로를 바꾸는 이유

의존 조건을 강화하면 잘못된 순서로 Grafana가 시작되는 문제는 막을 수 있지만, unhealthy 상태가 `docker compose up`의 실패로 전파되는 새로운 경로가 생긴다.

- 변경 전에는 Prometheus나 Loki가 준비되지 않아도 컨테이너가 시작되기만 하면 전체 `up -d`가 계속 진행될 수 있었다.
- 변경 후에는 Grafana가 두 서비스의 healthcheck 성공을 기다린다. 제한 시간 안에 healthy가 되지 않으면 Grafana를 시작할 수 없고 `up -d`가 non-zero 상태로 끝날 수 있다.
- readiness 실패는 자동 복구 대상인데, 이 반환값을 일반적인 치명적 오류와 동일하게 처리하면 재시도와 강제 재생성 코드에 도달하지 못한다.
- 따라서 “의존성 실패를 허용한다”가 아니라 “실패를 기록한 뒤 별도의 검증과 복구 절차가 최종 판정을 맡는다”는 구조가 필요하다.

## `set -e`와 명시적 실패 처리

`set -e`는 처리하지 않은 명령 실패에서 스크립트를 즉시 종료해 오류가 조용히 무시되는 일을 줄인다. 반대로 복구할 예정인 실패는 조건문 안에서 명시적으로 처리해야 한다.

- 단독으로 실행한 `$COMPOSE up -d`가 실패하면 `set -e` 때문에 다음 줄로 진행하지 않는다.
- `if ! $COMPOSE up -d; then ... fi`의 조건으로 실행하면 실패가 예상 가능한 분기로 처리되므로 스크립트가 종료되지 않는다.
- 강제 재생성 명령도 같은 이유로 조건문 안에서 실행한다. 재생성 명령 자체가 실패해도 후속 연결 검사를 수행해야 실제 복구 여부와 진단 로그를 남길 수 있다.
- 앱 헬스체크나 데이터소스 재검증까지 실패하면 그 지점에서 명시적으로 `exit 1`을 호출한다.
- `errexit`가 `if`, `while`, `until`, `!` 등의 조건에서 다르게 동작하는 규칙은 [GNU Bash 공식 매뉴얼](https://www.gnu.org/software/bash/manual/html_node/The-Set-Builtin.html)을 기준으로 한다.

## 앱 기동과 모니터링 기동 분리

배포의 핵심 경로와 복구 가능한 모니터링 경로를 분리하면 어느 실패를 즉시 중단하고 어느 실패를 복구할지 명확해진다.

- dev와 prod 모두 새 앱 컨테이너를 먼저 `up -d app`으로 교체한다.
- prod의 Nginx는 `--no-deps`로 시작한다. 앱 readiness는 이어지는 외부 `/health` 재시도가 판정하므로 Nginx 시작 명령이 먼저 실패를 확정하지 않게 한다.
- 이후 전체 Compose 스택을 올리되 실패를 조건문에서 기록하고 앱 헬스체크로 진행한다.
- 앱 헬스체크 실패는 기존처럼 직전 이미지로 롤백한다.
- 앱이 정상이라면 Grafana 컨테이너 내부에서 `prometheus:9090/-/ready`와 `loki:3100/ready`를 확인한다. 이 검사는 컨테이너 상태뿐 아니라 Docker DNS와 실제 HTTP 연결까지 함께 검증한다.
- `--no-deps`로 특정 서비스만 교체하는 방식은 [Docker의 production Compose 가이드](https://docs.docker.com/compose/how-tos/production/)에 설명된 서비스 단위 배포 방식과 같다.

## 자동 복구와 최종 실패 판정

자동 복구는 무한 재시도가 아니라 한 번의 제한된 재생성과 명확한 재검증으로 구성한다.

- 최초 데이터소스 검사는 5초 간격으로 최대 12회 수행한다.
- 계속 실패하면 Prometheus, Loki, Grafana만 `--force-recreate`로 한 차례 재생성한다.
- 재생성 명령이 non-zero로 끝나더라도 연결 재검증을 계속해 일시적인 Compose 반환 실패와 실제 서비스 장애를 구분한다.
- 재검증도 실패하면 세 컨테이너의 최근 로그를 출력하고 배포를 실패 처리한다.
- 앱, Alertmanager, Alloy 등 복구 대상이 아닌 컨테이너는 강제 재생성 범위에서 제외한다.

## 데이터 보존과 주의점

컨테이너 재생성과 데이터 삭제는 서로 다른 작업이다. 복구 과정에서는 컨테이너만 교체하고 named volume은 유지한다.

- `prometheus-data`, `loki-data`, `grafana-data`는 컨테이너 생명주기와 분리된 named volume이다.
- `docker compose up -d --force-recreate`는 컨테이너를 다시 만들지만 기존 named volume을 계속 연결한다.
- `docker compose down -v`는 volume까지 제거하므로 모니터링 복구 절차에서 사용하지 않는다.
- 자동 검사는 readiness와 DNS 연결을 보장하지만 대시보드 쿼리의 의미나 데이터 수집 완전성까지 검증하지 않는다. 배포 후 Backend Overview와 Logs 대시보드 확인은 별도의 운영 검증으로 남는다.

## 변경 전후의 배포 흐름

변경 전에는 전체 스택 기동 명령 하나가 성공해야만 후속 검사로 갈 수 있었다. 변경 후에는 앱과 모니터링의 성공 조건을 각 검증 단계가 맡는다.

- 변경 전: 전체 `up -d` → 앱 `/health` → 데이터소스 검사
- 문제점: 모니터링 dependency가 unhealthy면 전체 `up -d`에서 종료되어 데이터소스 복구 코드가 실행되지 않음
- 변경 후: 앱 기동 → 전체 스택 기동 시도 → 앱 `/health` → 데이터소스 검사 → 필요 시 모니터링 재생성 → 데이터소스 재검증
- 유지되는 검증: 앱 외부 헬스체크, Grafana 내부 데이터소스 HTTP 검사, 재검증 실패 시 로그 출력과 배포 실패
- 추가되는 비용: 모니터링이 unhealthy일 때 Compose healthcheck 대기와 최대 두 차례의 연결 재시도 시간이 발생함
