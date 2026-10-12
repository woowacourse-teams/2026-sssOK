# 중앙 모니터링 기동 검증 (#468, 1단계)

중앙 서버에서 다섯 서비스를 기동하고 자체 메트릭·테스트 로그를 검증하는 구성이다.
개발 node_exporter 연결 검증을 추가한다. 운영 연결, 앱 지표·로그 전환, ALB 전환,
Discord 발송, CI/CD 분리와 사양 하향은 이후 단계다.
개발 Compose에는 검증한 호스트 수집 설정을 반영하며, 기존 개발 Prometheus 내부 수집과
운영 구성은 유지한다.

## 확인한 인프라

| 서버 | 사설 IP | 현재 사양 | 서브넷 |
| --- | --- | --- | --- |
| 중앙 | 10.0.20.46 | t4g.micro 후보, RAM 약 906MiB 확인 | project-app-a |
| 개발 | 10.0.0.69 | t4g.small (기존 micro) | project-public-a |
| 운영 | 10.0.20.127 | t4g.medium (기존 micro) | project-app-a |

같은 TECHCOURSE-PROJECT VPC, ap-northeast-2a에 위치한다. 중앙은 Ubuntu 24.04 ARM64,
20GiB 루트 디스크, Swap 1GiB, project-app 보안 그룹, Session Manager 접속을 사용한다.
중앙 배포 디렉터리는 `/home/ubuntu/monitoring`이다. 서버에서 확인한 Docker는 29.9.0,
Compose는 5.6.0이다. 개발·운영 메트릭 보존은 15일, Loki 보존 환경변수는 각각 7일·30일이다.

project-app의 self 규칙은 같은 그룹을 가진 모든 서버 사이의 트래픽을 허용한다.
중앙만 접근 가능한 제한으로 오해하지 않는다. 중앙 Compose의 호스트 공개 포트는 모두
127.0.0.1에 묶어 서버 내부에서만 사용하며 ALB도 아직 연결하지 않는다.

## 자원 시험값

| 서비스 | 메모리 상한 | Go 메모리 목표 |
| --- | --- | --- |
| Prometheus | 192MiB | 150MiB |
| Loki | 256MiB | 200MiB |
| Grafana | 256MiB | 200MiB |
| Alertmanager | 64MiB | 48MiB |
| node_exporter | 48MiB | 36MiB |

상한 합계 816MiB는 예약량도 예상 사용량도 아니다. OS·Docker 메모리는 별도로 필요하고,
Go 메모리 목표는 프로세스 전체의 하드 한도가 아니다. 모든 컨테이너가 상한에 접근하면
1GiB 호스트가 부족할 수 있다. 기동 성공만으로 운영 사양을 확정하지 않는다.
수집량과 동시 조회를 늘리며 재시작·OOM·지속적인 Swap 입출력·응답 지연을 관찰한다.
Compose의 기본 Swap 동작을 사용하며 실제 할당은 `docker inspect`로 확인한다.
Grafana의 추가 플러그인 자동 설치는 꺼서 초기 다운로드와 저장 공간 사용을 줄인다.

Prometheus는 15일 또는 2GB 보존 제한 중 먼저 도달하는 기준을 적용한다. 크기 제한은
호스트 디스크 전체의 하드 한도가 아니므로 WAL·head 데이터와 여유 공간을 별도로 본다.
Loki는 기동 검증용 24시간 보존이며 **실제 개발·운영 로그를 보내지 않는다**.
환경별 7일·30일 보존과 수집량 제한을 구현한 뒤 전환해야 한다.

## 서버에 파일을 전달한 뒤 실행

이 디렉터리의 파일들을 `/home/ubuntu/monitoring`에 배치한다. 아직 GitHub에 푸시하지 않은
파일은 서버에서 git pull로 받을 수 없다. 전달 전에 파일·커밋 버전을 확인한다.

```bash
cd /home/ubuntu/monitoring
bash init.sh
docker compose config --quiet
docker compose pull
docker compose up -d
bash check.sh
docker compose ps
docker stats --no-stream
free -h
vmstat 1 6
```

`init.sh`는 난수 관리자 비밀번호를 `.env`에 권한 600으로 저장한다. 재실행해도 덮어쓰지
않는다. `.env`나 비밀번호가 포함된 `docker compose config` 출력을 채팅·Git에 공유하지 않는다.
Grafana 관리자 비밀번호는 최초 DB 생성 시 적용되므로 볼륨 생성 후 `.env`를 바꿔도 기존
관리자 비밀번호가 자동 변경되지는 않는다.

`check.sh`는 준비 상태, 다섯 대상의 up=1, CPU·메모리·Swap·루트 디스크 지표 및 Loki 테스트
로그의 저장·조회를 검사한다. Python 3와 curl을 사용한다. Docker Desktop에서 실행하면
node_exporter는 Linux VM을 관측하므로 EC2 호스트 지표는 EC2에서도 다시 검증한다.
이번 단계에서는 Grafana 데이터소스만 등록하며 대시보드와 브라우저 접속은 다음 단계다.

## 개발 연결과 재배포

개발 서버에서 임시 `dev-node.override.yml`로 검증한 CPU·메모리·Swap·부하·디스크 수집과
`127.0.0.1:9100` 포트를 `backend/docker-compose.dev.yml` 본문으로 옮겼다.
개발 CD는 기존처럼 이 Compose 하나로 실행하므로 별도 override가 필요 없다.
해당 변경이 develop에 병합되고 자동 배포되기 전까지는 종전 배포가 임시 설정을 되돌릴 수 있다.
새 Compose로 배포된 것을 확인한 뒤 서버에 남은 임시 override를 정리한다.
기존 개발 Prometheus는 같은 Docker 네트워크에서 계속 node-exporter:9100을 수집한다.
개발 CD는 앱·기존 모니터링 확인 후 `monitoring/check-dev-node.sh`로 호스트 지표와
Nginx의 비허용 IP 차단(403)을 검사한다. 실패 시 배포를 실패로 보고하되 정상 앱을 롤백하지 않는다.
이는 중앙에서의 실제 수집 성공을 보장하지 않으므로 중앙의 up 지표도 확인한다.

개발 호스트 Nginx용 `dev.nginx.conf`는 `/etc/nginx/conf.d/sssok-dev.conf`로 통합한다.
사용자가 확인한 기존 `conf.d/app.conf`와 `sites-available/dev-api.ssssok.com`은
server_name만 다르고 동작은 동일하다. 두 호스트 이름과 기존 SSE 설정을 하나의 80 포트
블록에 보존한다. 이 설정은 DNS나 운영 서버를 변경하지 않는다.
적용 전 `/etc/nginx` 전체를 권한 제한한 별도 경로에 백업한다. 기존 `conf.d/app.conf`와
`sites-enabled/dev-api.ssssok.com` 링크를 include 경로 밖으로 옮기고 새 파일을 설치한다.
별도 `sssok-node-metrics.conf`를 이미 설치했다면 중복되지 않도록 함께 비활성화한다.
공통 로그 형식 파일은 유지한다. `nginx -t`가 실패하면 reload하지 말고 기존 파일과 링크를
복원한다. 검사 성공 후 reload하고 두 Host 헤더의 `/health` 응답이 기존과 같은지 확인한다.
사설 주소 `10.0.0.69:19100`에서는 `/metrics`만 프록시하며 중앙 `10.0.20.46`만 허용한다.
개발 호스트에서 해당 사설 주소로 요청하면 403, 중앙 호스트에서 요청하면 200과 node 지표를
반환하는지 확인한다. 공유 보안 그룹 규칙은 수정하지 않는다. 설정 검사만으로 포트 충돌이나
실제 접근 제한이 검증되지는 않으므로 두 서버의 HTTP 검증이 필요하다.
롤백은 통합 파일을 제거하고 백업한 기존 파일과 링크를 원래 위치에 복원한 뒤,
`nginx -t` 성공 후 reload한다. `sites-enabled/*`는 확장자와 무관하게 읽으므로
그 디렉터리 안에서 `.bak`로 이름만 바꾸는 방식은 사용하지 않는다.
중앙 Prometheus의 node job에는 개발 `10.0.0.69:19100`을 environment=dev, server=dev로
등록한다. 개발 Nginx와 새 Compose 구성이 준비되지 않으면 이 대상은 DOWN이다.
컨테이너의 `promtool check config /etc/prometheus/prometheus.yml` 성공 후 Prometheus에
SIGHUP을 보내 반영한다. 중앙 호스트 curl 성공과 컨테이너에서의 수집 성공은 별도로 확인해야 한다.
`up{job="node",environment="dev",server="dev"}`가 1인지 확인한다.
`check.sh`는 중앙 자체 관측을 검사하므로 개발 수집 검증을 대신하지 않는다.

호스트 Nginx는 서버에서 검증한 설정을 수동 관리하며 개발 CD는 `/etc/nginx`를 덮어쓰지 않는다.
`dev.nginx.conf`는 해당 설정의 저장소 원본이다. 새 서버에서는 먼저 위 설치 절차를 수행해야 한다.
중앙 설정 역시 아직 수동 배포이며 전용 CI/CD 분리는 이후 단계다.

## 알림과 중지

중앙 서비스 수집 실패·메모리 여유 10% 미만·디스크 여유 15% 미만을 계산하지만,
Alertmanager 수신기는 외부 발송을 하지 않는다. 중앙 전체 장애를 외부에서 탐지하는
수단과 Discord 연결은 운영 전환 전에 추가해야 한다.

```bash
# 문제가 생기면 중앙 구성만 중지한다. 볼륨은 보존된다.
docker compose down
```

데이터를 유지하려면 `down -v`를 사용하지 않는다. 실제 로그 전환 전에 일관된 볼륨 백업,
복원 시험, 과거 데이터 조회 범위와 ALB·수집 경로 롤백 절차를 마련한다.

## 공식 참고

이번 단계 로컬 검증: Compose 해석, promtool 설정·규칙 테스트, amtool 설정, Loki 설정 검사,
네 서비스 기동·메트릭 수집, Grafana 데이터소스 연결·익명 접근 차단, Loki 테스트 로그
저장·조회, `.env` 권한·재실행 보존을 확인했다. Mac Docker의 root mount propagation 제한으로
node_exporter 기동은 로컬에서 확인하지 못했다. EC2에서 `bash check.sh`로 최종 확인한다.
로컬 시험용 컨테이너와 볼륨은 정리하며 실제 서버 데이터는 사용하지 않는다.

- [컨테이너에서 호스트 수집](https://github.com/prometheus/node_exporter#docker)
- [Prometheus 보존 정책](https://prometheus.io/docs/prometheus/latest/storage/)
- [Grafana Docker 설정](https://grafana.com/docs/grafana/latest/setup-grafana/configure-docker/)
