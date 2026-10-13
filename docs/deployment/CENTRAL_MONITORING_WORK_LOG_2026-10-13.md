# 중앙 모니터링 분리 작업 기록과 후속 계획

> 이 문서는 호스트 지표 연결까지의 초기 기록이다. 이후 앱·로그·Alloy 지표 중앙 연결과 개발 Docker Nginx 전환이 진행됐다. 최신 완료 상태와 다음 작업은 [중앙 모니터링 인수인계](CENTRAL_MONITORING_HANDOFF_2026-10-13.md)를 먼저 확인한다.

중앙 모니터링 서버를 구축하고 개발·운영·중앙 서버의 호스트 지표를 한 Grafana에서 확인했다. 앱 지표와 로그의 중앙 이전, 배포 자동화, 기존 모니터링 제거는 아직 남아 있다.

- 정리 기준일: 2026년 10월 13일. 날짜를 걸쳐 이어진 10월 12일 사전 점검부터 이번 대화의 마무리까지 포함한다.
- 관련 작업: [이슈 468](https://github.com/woowacourse-teams/2026-sssOK/issues/468).
- 현재 완료 지점: **세 서버의 호스트 지표 수집과 중앙 대시보드 조회**.
- 중요한 유지 조건: **기존 운영 지표·로그는 분석을 위해 기존 서버와 볼륨에 유지한다.**
- 상태 구분: 서버 적용 완료, 레포 반영, 실제 CI/CD 배포 완료는 서로 다른 단계다.

## 작업 목적과 인스턴스 운영 원칙

앱과 모니터링이 같은 서버의 자원을 경쟁하는 구조를 분리하고, 관측 결과를 근거로 적정 인스턴스 크기를 결정한다.

- 기존 개발·운영에는 앱과 Prometheus·Loki·Grafana 등이 함께 실행되고 있었다.
- 모니터링 분리는 앱 자원 확보, 앱 서버 장애 시 관측의 독립성, 환경 간 비교를 위한 작업이다.
- 작은 인스턴스로 시작하고 부하를 점진적으로 늘려 한계를 확인한다.
- CPU 사용률 하나로 증설하지 않는다. 응답 시간, 작업 완료 시간, 오류, 대기열, 메모리·Swap, 비용을 함께 판단한다.
- 같은 앱이라도 요청량, 실행 시간, JVM 설정·힙 사용, 캐시, 작업량이 달라 메모리가 다를 수 있다. 이번 출력만으로 차이의 원인을 확정하지 않았다.
- 모니터링이 켜졌다는 사실만으로 작은 인스턴스가 실제 운영 부하를 감당한다고 판단하지 않는다.

## 서버별 구성과 역할

개발·운영은 서비스 실행과 현장 수집을 담당하고, 중앙은 수집한 정보를 저장·조회하는 역할을 맡는다. 현재는 이전 기간이므로 기존 모니터링도 병행 실행 중이다.

| 구분 | 사설 IP | 인스턴스 기준 | 서브넷 | 작업 디렉터리 |
| --- | --- | --- | --- | --- |
| 중앙 | 10.0.20.46 | t4g.micro로 시작하는 검증 구성, 사용 가능 RAM 약 906MiB | project-app-a | /home/ubuntu/monitoring |
| 개발 | 10.0.0.69 | t4g.small, 이전 micro | project-public-a | /home/ubuntu/app |
| 운영 | 10.0.20.127 | t4g.medium, 이전 micro | project-app-a | /home/ssm-user/app |

- 모두 t4g 계열이며 중앙 OS는 Ubuntu 24.04.4 LTS, 아키텍처는 aarch64였다.
- 세 서버는 TECHCOURSE-PROJECT VPC, 서울 리전, ap-northeast-2a를 사용한다.
- 운영 IP 10.0.20.127과 중앙 IP 10.0.20.46을 혼동하지 않는다.
- 서버 명령은 프롬프트의 IP와 디렉터리를 확인하고 실행한다. 실제로 중앙 서버에서 개발 파일 경로를 사용해 실패한 적이 있었다.

## 사전 자원 점검

인스턴스 크기를 먼저 정하지 않고 현재 메모리·Swap·디스크·컨테이너 사용량을 확인했다. 아래 수치는 각각 다른 시점의 관측값이며 부하 시험 결과가 아니다.

| 항목 | 개발 | 운영 |
| --- | --- | --- |
| RAM 전체 | 약 1.8GiB | 약 3.7GiB |
| RAM 사용 / 가용 | 약 1.4GiB / 412MiB | 약 2.0GiB / 1.7GiB |
| Swap 전체 / 사용 | 1GiB / 510MiB | 2GiB / 84MiB |
| 앱 컨테이너 메모리 | 약 631MiB | 약 1.03GiB |
| 루트 디스크 | 19GB 중 9.7GB 사용, 8.7GB 여유 | 19GB 중 11GB 사용, 7.4GB 여유 |
| Prometheus 데이터 볼륨 | 약 229MB | 약 360MB |
| Loki 데이터 볼륨 | 약 3.58MB | 약 8.54MB |
| Grafana 데이터 볼륨 | 약 502MB | 약 502MB |

- `free -h`, `docker stats`, `vmstat`, `df -h`, `docker system df -v`로 확인했다.
- 짧은 vmstat 측정에서 개발·운영의 지속적인 Swap 입출력은 나타나지 않았다.
- Docker 이미지와 데이터 볼륨 크기를 구분했다. Grafana·Alloy 이미지, 현재·직전 앱 이미지도 디스크를 차지한다.
- 기존 이미지·데이터를 정리하는 작업은 이번 점검에서 실행하지 않았다.
- 초기 보존 설정은 Prometheus 개발·운영 모두 15일, Loki 환경변수는 개발 168h, 운영 720h였다.

## VPC와 서브넷 및 라우팅 개념

VPC는 AWS의 사설 네트워크이고, 서브넷은 그 안의 주소 구역이다. 라우팅 테이블은 목적지별로 트래픽을 보낼 경로를 결정한다.

- VPC 내부 대역은 `10.0.0.0/16`이고 두 서브넷에 `local` 경로가 있었다.
- `project-public-a`: 외부 기본 경로 `0.0.0.0/0`가 인터넷 게이트웨이로 연결된다.
- `project-app-a`: 외부 기본 경로가 NAT Gateway로 연결된다.
- 같은 VPC의 개발·운영·중앙 통신은 사설 IP와 local 경로를 사용한다. 이 경로에 NAT가 필요한 것은 아니다.
- 라우팅 경로가 있어도 보안 그룹, 네트워크 ACL, 방화벽, 프로세스의 수신 주소가 허용해야 실제 연결된다.
- 사설 IP는 내부 통신 주소다. 개발 서버가 public 서브넷에 있어도 중앙과는 사설 IP로 통신할 수 있다.
- NAT Gateway는 사설 서버가 외부로 시작하는 통신과 그 응답을 처리하는 데 사용한다. 인터넷 사용자가 중앙 Grafana에 들어오는 경로는 ALB다.
- 공식 개념: [AWS 라우팅 테이블](https://docs.aws.amazon.com/vpc/latest/userguide/VPC_Route_Tables.html), [NAT Gateway](https://docs.aws.amazon.com/vpc/latest/userguide/vpc-nat-gateway.html).

## 조직 네트워크 규칙과 중앙 배치 결정

조직에서 지정한 네트워크 자원을 재사용하고, 중앙 Grafana를 ALB를 통해 접근하는 EC2로 배치했다.

- 직접 접근 EC2: project-public 서브넷·보안 그룹을 사용한다.
- LB를 통해 접근하는 EC2: project-app 서브넷·보안 그룹을 사용한다.
- LB는 project-lb 서브넷·보안 그룹과 지정된 WAF를 사용하는 규칙이 있다.
- 허용 인스턴스 상한은 t4g.medium이다. 중앙은 작은 사양부터 검증한다.
- SSM 등 AWS 연동에는 ec2-project 역할을 사용하는 조직 규칙을 확인했다.
- 개발·운영에는 project-public과 project-app 보안 그룹이 함께 연결돼 있었다. 이번 작업에서 이를 일괄 정리하지 않았다.
- project-app의 self 허용은 같은 보안 그룹의 모든 서버에 적용된다. 중앙 전용 허용 규칙으로 해석하면 안 된다.
- 중앙은 project-app-a에 만들고 퍼블릭 IPv4 자동 할당을 비활성화했다. 관리에는 Session Manager, 화면 접근에는 ALB를 사용한다.
- 공인 접속이 나중에 필요하면 네트워크 경로와 공인 주소 부여 방식을 다시 검토한다. 기존 인스턴스에서 자동 할당 옵션만 간단히 다시 켜면 된다고 가정하지 않는다.

## 중앙 서버 기본 설정

중앙 서버에 모니터링을 실행할 기반을 마련하고 재부팅 후에도 기본 기능이 유지되는지 확인했다.

- Docker와 Compose 설치 후 ARM64 hello-world 컨테이너 실행에 성공했다.
- 서버에서 확인한 버전은 Docker 29.9.0, Compose 5.6.0이다. 최신 권장 버전을 의미하는 기록은 아니다.
- Docker 로그 드라이버는 json-file이며 파일 크기·개수 제한을 설정했다.
- ubuntu 사용자의 Docker 실행과 재부팅 후 Docker active 상태를 확인했다.
- 초기 디스크는 약 19GB 중 1.8GB 사용, 약 17GB 여유였다.
- `/swapfile` 1GiB를 만들고 권한 제한, Swap 활성화, fstab 등록을 진행했다.
- `findmnt --verify`는 파싱 오류 0, 오류 0이었다. 일반 파일을 Swap으로 사용하는 데 대한 경고가 1건 있었고 재부팅 후 활성화도 확인했다.
- SSM 기본 셸에서 여러 줄 입력이 꼬이는 상황은 `sudo -iu ubuntu`로 사용자·로그인 셸을 정리해 진행했다.
- `s: not found`, `-: command not found`, 깨진 문자가 붙은 ls 등은 붙여넣기 입력 오류였으며 Docker 장애 증거가 아니었다.

## 중앙 모니터링 구성 요소

지표와 로그의 저장소, 조회 화면, 알림 처리기는 서로 다른 역할을 한다.

| 구성 요소 | 역할 | 중앙 메모리 제한 |
| --- | --- | --- |
| Prometheus | 대상을 주기적으로 조회하고 수치 지표 저장 | 192MiB |
| node_exporter | 운영체제·하드웨어 지표 제공 | 48MiB |
| Grafana | Prometheus·Loki 데이터를 조회해 화면 구성 | 256MiB |
| Loki | 로그 저장·조회 | 256MiB |
| Alertmanager | 전달받은 알림의 묶음·전송 관리 | 64MiB |
| Nginx | ALB 요청을 중앙 Grafana로 연결 | 32MiB |

- 중앙 node_exporter는 중앙 서버 자체를 관측한다. 개발·운영 node_exporter는 각 서버를 관측한다.
- Prometheus가 HTTP 요청으로 지표를 가져오는 방식을 scrape라고 한다.
- 로그는 향후 개발·운영 Alloy에서 중앙 Loki로 보내는 경로를 구성해야 한다. 호스트 지표 연결과 별개다.
- node_exporter는 과거 지표를 저장하는 데이터베이스가 아니다. 과거 값은 Prometheus에 저장된다.
- 컨테이너에서 호스트를 관측하려면 호스트 파일시스템·proc·sys와 필요한 namespace 접근을 설정해야 한다. [node_exporter 공식 안내](https://github.com/prometheus/node_exporter).
- 중앙 Prometheus는 초기 15일·2GB 제한, 중앙 Loki는 테스트용 24시간 보존 구성이다. **중앙 Loki를 기존 운영 30일 로그 저장소로 바로 전환하면 안 된다.**
- `check.sh`로 중앙 다섯 대상 수집, 호스트 지표, Loki 테스트 로그 저장·조회를 확인했다.
- Alertmanager 기동과 실제 외부 알림 발송은 다르다. Discord 실발송은 아직 연결하지 않았다.

## 중앙 자원 사용 결과와 해석

초기 중앙 구성은 실행됐지만 실제 서비스 부하를 모두 받는 최종 상태는 아니다.

- 초기 Grafana 메모리는 약 224MiB로 256MiB 제한의 약 87%였고, 이후 약 190MiB로 낮아졌다.
- 중앙 RAM 가용량은 약 330~341MiB, Swap 사용량은 약 258~265MiB였다.
- 검사 시 컨테이너 재시작 횟수는 0, OOMKilled는 false였다. 이것이 이후 모든 시점의 무장애를 보장하지는 않는다.
- 초기 30초 vmstat에서는 지속적인 Swap-out이 없고 간헐적 Swap-in이 보였다. CPU는 대체로 여유가 있었다.
- 이후 대시보드에서는 CPU가 일시적으로 약 10%, 가용 메모리가 30%대, 디스크 사용률 약 33%, Swap 입출력 약 400페이지/초까지 보였다.
- 해당 Swap 패널은 읽기·쓰기 합계의 5분 평균이다. 방향 분리 없이 지속적인 메모리 부족으로 단정하지 않는다.
- Grafana 재시작·조회와 증가 시점의 관련성은 가설이며 원인으로 확정하지 않았다.
- 메모리 제한은 예약량이 아니다. 가용 메모리는 free 값과 다르며 회수 가능한 캐시 등을 고려한 값이다.
- `vmstat` 첫 행은 부팅 이후 평균이고 이후 행이 측정 간격별 값이다. Swap 점유량과 현재 Swap 입출력은 구분한다.
- t4g 지속 부하 검증에는 CPU 크레딧 잔액과 초과 사용도 포함한다. [AWS CPU 크레딧 지표](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/burstable-performance-instances-monitoring-cpu-credits.html).

## 중앙 Grafana 웹 접속 구성

기존 운영 Grafana를 유지하면서 같은 도메인의 `/central/` 경로로 중앙 Grafana에 접속하도록 했다.

- 중앙 URL: `https://monitor.ssssok.com/central/`.
- 기존 운영 URL: `https://monitor.ssssok.com/`.
- 기존 ALB `sssok-alb`와 monitor 도메인의 인증서를 재사용했다. 중앙을 위해 별도 인증서를 새로 만드는 방안은 채택하지 않았다.
- 중앙 대상 그룹은 HTTP 80, 상태 검사 경로 `/health`, 성공 코드 200으로 구성하도록 진행했다.
- 리스너에는 호스트 `monitor.ssssok.com`과 경로 `/central`, `/central/*`가 함께 일치하는 규칙을 추가했다.
- 기존 우선순위 10의 monitor 규칙보다 앞서도록 빈 우선순위 5를 안내했다. 별도 ALB 전체 설정 덤프를 보관한 것은 아니며 최종 브라우저 접속으로 경로를 검증했다.
- 호스트와 경로 조건은 함께 만족해야 하고, 두 경로 값은 둘 중 하나가 일치하면 된다. [ALB 조건 안내](https://docs.aws.amazon.com/elasticloadbalancing/latest/application/rule-condition-types.html).
- 경로 입력 오류가 한 번 있었고 다시 입력하도록 안내했다. 숨은 문자 여부 등 실제 원인은 확정하지 않았다.
- 중앙 Nginx는 사설 IP의 80번을 열고 Grafana는 호스트 loopback 3000번을 유지한다.
- `/health`는 Grafana 건강 상태를 프록시하며 실제 database ok와 HTTP 200을 확인했다.
- Grafana ROOT_URL과 SERVE_FROM_SUB_PATH를 설정하고 `/central/` 경로를 유지해서 전달한다.
- 기존 Grafana와 로그인 쿠키 이름을 구분하고 중앙 쿠키는 HTTPS에서만 전달하도록 설정했다.
- `/central`에서 `/central/`로의 리다이렉트는 상대 경로를 사용하도록 했다. Grafana Live용 WebSocket 헤더도 설정했다.
- 관리자 ID는 admin이고 비밀번호는 중앙 `.env`에 저장한 초기 생성 값이다. 비밀번호는 문서나 Git에 기록하지 않는다.
- 프록시와 하위 경로 개념: [Grafana 공식 안내](https://grafana.com/tutorials/run-grafana-behind-a-proxy/).

## CLI와 브라우저 접근 중 겪은 문제

로컬 AWS 인증 문제로 SSM 포트 포워딩 경로를 끝내지 못했고, 기존 ALB를 통한 브라우저 접근을 선택했다.

- Mac에서 STS 호출이 InvalidClientTokenId로 실패했다.
- AWS 로그인 시도에서도 브라우저 Bad Request가 발생했다.
- 조직 계정은 별칭을 통한 IAM 사용자 로그인 방식이고, 사용자는 관련 IAM 권한을 확인할 수 없다고 했다.
- 정확한 원인을 권한 부족 하나로 단정하지 않았고, 새 액세스 키 발급이나 IAM 권한 변경은 수행하지 않았다.
- AWS 콘솔 Session Manager로 서버 셸 접속은 가능했다. 로컬 CLI 인증 성공과 별개다.
- 최종적으로 ALB 경로를 사용해 중앙 Grafana 로그인과 대시보드 조회에 성공했다.

## 개발 호스트 지표 연결

개발 Nginx는 호스트에 직접 설치돼 있으므로, 호스트 loopback 포트를 통해 Docker node_exporter에 연결했다.

- 경로: `중앙 → 10.0.0.69:19100 → 호스트 Nginx → 127.0.0.1:9100 → node_exporter`.
- 기존 node_exporter는 filesystem 수집만 활성화돼 있었다.
- CPU·meminfo·vmstat·loadavg를 추가하고 호스트 proc·sys 경로, host PID, 읽기 전용 rslave 마운트를 설정했다.
- 초기에는 `dev-node.override.yml`로 적용하고, 같은 설정을 레포 기본 개발 Compose에도 반영했다.
- 개발의 기존 `conf.d/app.conf`와 `sites-enabled/dev-api.ssssok.com`은 같은 앱으로 전달하는 설정이었다.
- `/etc/nginx` 전체를 백업한 뒤 두 도메인을 `sssok-dev.conf` 하나에 통합했다. 공통 로그 형식 파일은 유지했다.
- 기존 활성 파일·링크는 include 경로 밖의 `sssok-disabled-468`로 이동했다. sites-enabled 안에서 확장자만 바꾸면 여전히 읽힐 수 있다.
- 중간에 기존 설정 복원 후 통합 설정을 재적용했고, `nginx -t`와 reload를 거쳐 두 도메인 health 200을 확인했다.
- metrics는 중앙 IP 10.0.20.46만 허용했다. 개발 자체 요청은 403, 중앙 요청은 200이었다.
- 중앙 Prometheus의 node job에 environment=dev, server=dev를 추가하고 설정 검사·SIGHUP 적용 후 up=1을 확인했다.
- 개발 가용 메모리 지표도 실제 조회됐다. 한 시점의 중앙 쿼리 값은 약 444.93MiB였다.

## 개발 수집 장애와 재발 방지

대시보드에서 dev=0과 개발 지표 누락을 발견했다. 중앙 Prometheus의 lastError는 HTTP 502였다.

- 개발 Nginx까지는 요청이 도달했지만 뒤쪽 node_exporter 연결이 실패했다.
- node_exporter는 실행 중이었으나 Docker 출력에 `9100/tcp`만 있고 호스트 포트 연결이 없었다.
- 개발 호스트에서 `127.0.0.1:9100/metrics` 요청은 연결 실패였다.
- inspect 결과 컨테이너 생성에 기본 Compose만 사용됐고, 추가 override 파일은 서버에 남아 있었다.
- 기본 파일과 override를 함께 지정해 node_exporter만 재생성했다. 이후 중앙 up=1 복구를 확인했다.
- 확인된 원인은 추가 설정이 실행 구성에 포함되지 않은 것이다. 특정 배포 실행과의 대응 관계는 배포 이력으로 추가 확인해야 한다.
- `dev-node.override.yml` 같은 임의 이름 파일은 놓아두기만 해서는 적용되지 않는다. 명시적으로 `-f`에 넣어야 한다. 기본 자동 탐색 파일과 구분한다. [Compose 병합 규칙](https://docs.docker.com/compose/how-tos/multiple-compose-files/merge/).
- 근본 완료 조건은 레포 수정뿐 아니라 해당 코드의 병합·배포와 후속 배포 후 수집 유지 확인이다.

## Docker 포트와 네트워크 개념

호스트와 컨테이너는 기본적으로 별도 네트워크 공간을 사용한다. 같은 숫자의 포트라도 자동 연결되지 않는다.

- `9100/tcp`: 컨테이너 포트 정보이며 호스트에 게시됐다는 의미가 아니다.
- `127.0.0.1:9100->9100/tcp`: 호스트 내부 접속을 컨테이너 9100번으로 연결한다.
- `127.0.0.1`은 명령을 실행하는 네트워크 공간 자신이다. Nginx 컨테이너 안에서는 호스트가 아니라 Nginx 컨테이너 자신이다.
- 운영은 Nginx와 node_exporter가 같은 `app_sssok` 네트워크에 있어 `node-exporter:9100`으로 연결했다.
- `--no-deps`로 대상 서비스만 갱신했다. 포트 게시 변경은 Nginx reload만으로 적용되지 않아 컨테이너 재생성이 필요했다.
- 정규식의 `$$`는 Compose를 거쳐 실제 `$`로 전달하기 위한 표기다.
- 공식 개념: [Docker 포트 게시](https://docs.docker.com/engine/network/port-publishing/).

## 운영 Nginx 정리와 호스트 지표 연결

운영에서는 Nginx와 node_exporter가 모두 Docker로 실행된다. 기존 API와 Grafana 경로를 유지하면서 지표 전용 포트를 추가했다.

- 실제 Compose 경로는 `/home/ssm-user/app/docker-compose.prod.yml`이다.
- 운영 Nginx는 `/home/ssm-user/app/nginx.conf`를 컨테이너의 `conf.d/default.conf`에 마운트한다.
- 서버 Nginx 파일과 레포의 실행 지시문이 동일한 것을 확인했다.
- 레포 Nginx의 긴 주석을 정리해 186줄에서 111줄로 줄였다. 이 시점에는 실행 지시문을 변경하지 않았다. 이후 지표 전용 블록이 추가돼 최종 줄 수는 달라졌다.
- 광범위한 사설 대역을 신뢰하는 기존 realip 설정을 ALB 전용 신뢰로 단정한 주석을 바로잡았다. 신뢰 대역 자체를 이번에 축소하지는 않았다.
- ubuntu 계정의 Docker permission denied는 sudo를 붙여 조회하면서 해결했다. 권한·그룹 구성을 통일한 것은 아니다.
- 운영 Compose와 Nginx 설정을 타임스탬프가 붙은 파일로 각각 백업했다. **설정 백업이며 관측 데이터 백업은 아니다.**
- 운영 node_exporter의 기본 Compose에 CPU·메모리·Swap·부하 수집을 추가했다. 별도 운영 override는 만들지 않았다.
- 수집기만 재생성하고 Nginx 컨테이너에서 `node-exporter:9100/metrics` 조회를 확인했다.
- 두 CPU의 누적 시간, 메모리, Swap 누적 카운터, 루트 디스크, load1이 출력됐다.
- 운영 가용 메모리는 한 시점 약 1.86GB였다. CPU 누적 초와 Swap 누적 카운터는 그 자체가 현재 사용률·초당 입출력이 아니다.

## 운영 지표 접근 제한과 검증

운영 사설 IP의 19100번을 중앙 수집 전용으로 사용한다. 기존 요청 헤더 처리와 별개로 실제 접속 주소를 기준으로 제한했다.

- 경로: `중앙 → 10.0.20.127:19100 → Docker Nginx → node-exporter:9100`.
- Nginx는 컨테이너 내부 19100번을 받고 Compose는 `10.0.20.127:19100:19100`을 게시한다.
- `$realip_remote_addr`를 geo로 판정해 중앙 IP만 허용한다. 기존 realip 설정으로 치환된 IP를 접근 제한에 그대로 쓰지 않는다.
- `/metrics`만 프록시하고 다른 경로는 404를 반환한다. Docker DNS resolver를 사용해 node_exporter 주소 변경에 대응한다.
- 원래 접속 주소의 의미: [Nginx realip 공식 문서](https://nginx.org/en/docs/http/ngx_http_realip_module.html).
- nginx 문법·Compose 검사와 호스트 19100 포트 미사용 여부를 확인한 뒤 Nginx만 재생성했다.
- 이 작업은 짧은 접속 중단·SSE 연결 종료 가능성을 안내하고 진행했다. 앱 컨테이너나 기존 데이터 저장소를 재생성하는 명령은 사용하지 않았다.

| 검증 | 결과 | 의미 |
| --- | --- | --- |
| 운영 로컬 API health | 200 | 적용 후 기존 앱 연결 정상 |
| 중앙에서 운영 metrics | 200 및 지표 출력 | 중앙 수집 경로 연결 |
| 운영 자신이 metrics 요청 | 403 | 중앙 이외 접속 차단 |
| 운영에서 X-Forwarded-For를 중앙 IP로 지정 | 403 | 이 시험 경로에서 헤더 변경으로 우회되지 않음 |
| 중앙 Prometheus prod up 조회 | 1 | 컨테이너 안의 Prometheus 실제 수집 성공 |

- 중앙 prometheus.yml의 node job에 운영 주소와 environment=prod, server=prod를 추가했다.
- promtool 검사 후 SIGHUP으로 중앙 설정을 다시 읽게 했고, 사용자가 up=1을 확인했다.
- 모든 네트워크 출발지에 대한 포괄적인 보안 시험을 수행했다는 의미는 아니다.

## 대시보드 등록과 현재 화면

중앙 Grafana에는 파일 가져오기로 호스트 대시보드를 등록했고, 운영 수집 후 prod를 포함하도록 덮어썼다.

- 대시보드: `sssOK Host Overview`, UID `sssok-host-overview`.
- 데이터 소스 UID: prometheus.
- Grafana 로그인 성공과 대시보드 등록은 별개다. 처음 빈 화면은 아직 대시보드를 가져오지 않았기 때문이었다.
- 최초 쿼리는 monitoring과 dev만 포함했다. 이후 다섯 패널 모두 monitoring·dev·prod를 포함하도록 수정했다.
- 사용자가 업데이트된 JSON을 다시 가져와 완료했다고 확인했다.
- JSON 덮어쓰기는 대시보드 정의 변경이며 Prometheus·Loki 데이터를 삭제하지 않는다.
- 중앙 수집을 시작하기 전 과거 데이터가 중앙 그래프에 자동으로 생기지는 않는다.

| 패널 | 읽는 방법과 한계 |
| --- | --- |
| 지표 수집 상태 | up=1은 지표 조회 성공, 0은 실패. 앱의 전체 정상 여부와 같지 않음 |
| CPU 사용률 | idle을 제외한 5분 rate 기반 값. 앱만의 CPU가 아니라 호스트 값 |
| 가용 메모리 비율 | MemAvailable / MemTotal. 낮을수록 여유가 적음 |
| 루트 디스크 사용률 | 루트의 사용 가능 공간을 기준으로 계산 |
| Swap 입출력 | 읽기·쓰기 합계의 5분 rate, 단위는 페이지/초. 방향 분리는 후속 작업 |

- 새 대상의 rate 그래프는 샘플이 쌓여야 나타난다.
- 환경별 범례만 사용하므로 나중에 같은 환경의 서버가 늘어나면 server·instance별 구분도 보완한다.
- `dashboards.yml`은 작성돼 있지만 현재 중앙 기본 Compose에는 해당 provisioning 파일과 JSON 마운트가 연결되지 않았다. **자동 등록 완료가 아니라 수동 Import 완료 상태다.**

## 기존 운영 데이터 보존 결정

가설과 기능 지표 분석에 필요한 기존 운영 데이터는 그대로 유지한다. 보존 기간 변경이나 추가 백업은 사용자의 요청에 따라 이번 단계에서 진행하지 않았다.

| 운영 저장소 | 볼륨 | 컨테이너 경로 |
| --- | --- | --- |
| Prometheus | app_prometheus-data | /prometheus |
| Loki | app_loki-data | /loki |
| Grafana | app_grafana-data | /var/lib/grafana |

- 실제 볼륨 경로는 `/var/lib/docker/volumes/<볼륨 이름>/_data`로 확인했다.
- 설정 파일, Grafana provisioning, 파일 대시보드는 호스트 bind mount에도 존재한다.
- 컨테이너와 named volume의 수명은 다르다. 일반적인 컨테이너 교체가 볼륨의 과거 데이터를 자동 삭제하지는 않는다. [Docker 볼륨](https://docs.docker.com/engine/storage/volumes/).
- 기존 운영 Prometheus는 실행 옵션으로 15일 보존을 재확인했다.
- 기존 운영 Loki는 retention_enabled=true, 삭제 지연 2h, 환경변수 기반 보존 설정을 확인했다. 이전 출력은 720h였으나 마지막 점검 시점의 실행 환경 재확인은 하지 않았다.
- **볼륨이 남아 있어도 보존 기간이 지나면 데이터는 삭제될 수 있다.** [Prometheus 저장 정책](https://prometheus.io/docs/prometheus/latest/storage/), [Loki 보존 정책](https://grafana.com/docs/loki/latest/operations/storage/retention/).
- 운영 디스크 여유는 마지막 확인 시 7.4GB였다. 보존 기간을 무제한으로 늘리지 않았다.
- 기존 운영 자료는 기존 Grafana에서 조회한다. 중앙 서버는 별도 저장소에 신규 수집한다.
- 지난주부터 모든 자료가 빠짐없이 있는지 실제 조회·전수 검증한 것은 아니다. 새 백업·복원 시험도 하지 않았다.
- 이번 작업에서 기존 운영 데이터 볼륨 삭제, `down -v`, 볼륨 정리는 실행하지 않았다.
- 사용자의 다음 분석이 끝나기 전 기존 모니터링 제거를 진행하지 않는다. 이후에도 데이터 보존·복구 방안을 합의하고 제거한다.

## 레포 변경과 배포 상태

서버에서 작동을 확인한 설정을 레포에도 반영하고 있지만, 이번 작업 전체가 병합·배포된 상태는 아니다.

- 작업 브랜치: `chore/be-#468-central-monitoring-instance`.
- 앞서 커밋·푸시한 변경: `fd684928` 중앙 초기 구성, `b447bf33` 개발 호스트 지표 수집 설정 고정.
- 아래 후속 변경과 이 문서는 기준 시점에 미커밋 상태다. 이번 문서 작성으로 커밋·푸시·PR 생성은 하지 않는다.
- 초기 문서의 미연결·loopback 전용·SSM 조회 설명은 당시 단계 기록이다. 현재 상태는 이번 기록과 실제 설정을 기준으로 구분한다.

| 파일 | 반영 내용 | 상태 |
| --- | --- | --- |
| backend/docker-compose.dev.yml | 개발 호스트 수집 항목·loopback 9100 | 앞선 커밋에 포함, 실제 기본 Compose 배포 검증 남음 |
| backend/docker-compose.prod.yml | 운영 호스트 수집·Nginx 사설 19100 | 서버 수동 적용, 레포 미커밋 |
| backend/nginx.conf | 주석 정리·운영 지표 전용 서버·실제 주소 ACL | 기능 서버 적용, 정리된 파일 전체 동일성은 후속 확인 |
| .github/workflows/backend-ci.yml | 개발 관측 구성 검사 job | 레포 미커밋, GitHub 실행 전 |
| .github/workflows/deploy-dev.yml | Nginx 원본 사전 비교, 기존 수집 검증 유지 | 후속 변경 미커밋, 실제 배포 전 |
| backend/monitoring/validate-dev-node.sh | 포트·마운트·수집 옵션·Nginx 문법 검사 | 로컬 검증 완료 |
| backend/monitoring/check-dev-node.sh | 개발 로컬 지표·Nginx 403 검사 | 앞선 커밋에 포함 |
| backend/monitoring/central/check-dev.sh | 중앙에서 dev up와 필수 지표 확인 | 작성·모의 실패 검사 완료, 서버 실행 결과 별도 필요 |
| backend/monitoring/central/prometheus.yml | 중앙·개발·운영 호스트 대상 | 서버 적용 확인, 운영 추가분 미커밋 |
| backend/monitoring/central/compose.web.yml | 중앙 Grafana 하위 경로·Nginx | 서버 적용, 레포 미커밋 |
| backend/monitoring/central/nginx.conf | 중앙 ALB health·Grafana 프록시 | 서버 적용, 레포 미커밋 |
| backend/monitoring/central/host-overview.json | 세 환경의 호스트 패널 | 수동 Import 완료, 레포 미커밋 |
| backend/monitoring/central/dashboards.yml | 파일 대시보드 provider 정의 | 파일만 작성, Compose 마운트 미연결 |
| backend/monitoring/central/README.md | 설치·검증·개발 배포 절차 | 후속 상태에 맞춘 전체 정리 필요 |

## CI와 CD 검증의 의미

CI는 설정과 코드가 배포 가능한지 검사하고, CD는 실제 서버에 적용한다. 서버의 실제 수집 성공은 배포 후 별도로 확인해야 한다.

- 개발 CI는 기본 Compose에 loopback 9100, host PID, 읽기 전용 마운트, 필요한 collector가 있는지 검사한다.
- CI에는 개발 사설 IP가 없으므로 Nginx 검사 사본에서 listen 주소만 loopback으로 바꾼다. 원본의 실제 사설 IP listen은 별도로 검사한다.
- 초기 Nginx 검사에서 개발 IP가 없어 bind 오류가 났고, 검사 사본을 사용하도록 보완한 뒤 통과했다.
- 개발 CD 사전 검사에 레포 Nginx와 서버 Nginx의 정확한 파일 비교를 추가했다. 주석·공백 차이도 실패하므로 최초 원본 적용 절차가 필요하다.
- 호스트 Nginx를 CD가 자동 덮어쓰지는 않는다. 차이가 나면 앱 교체 전에 중단한다.
- 배포 후 개발 로컬 지표와 접근 제한 검사는 앱이 정상일 때 앱을 되돌리는 대신 배포 실패로 보고하도록 작성했다.
- 로컬에서 Compose·Nginx 검사, YAML·셸 문법, 포트 누락 시 실패, 중앙 수집 실패·필수 지표 누락 시 실패를 확인했다.
- 실제 GitHub CI 결과, 후속 배포 후 안정성, 운영용 동일 검증, 중앙 전용 CD는 남아 있다.
- 중앙 웹 구성 관리에는 `compose.yml`과 `compose.web.yml`을 함께 지정해야 한다. 기본 파일만 적용해 하위 경로 설정이 빠지는 재발을 막아야 한다.

## 개발과 운영을 동일하게 맞출 범위

설정 구조와 관측 기준은 통일하고, 환경에 따라 필요한 값은 분리하기로 했다. 아직 통일 작업은 완료하지 않았다.

- Nginx 실행 방식: 개발 호스트 설치형과 운영 Docker형 차이를 정리한다. 개발도 Docker로 맞추는 방향을 검토한다.
- 수집기: 버전, collector, 호스트 접근 방법, 메모리 제한 기준을 정리한다.
- 앱 지표: 같은 endpoint·지표 이름·라벨 규칙을 사용한다.
- 로그: 형식, service·environment 라벨, Alloy 수집 방식을 통일한다.
- 설정 관리: 공통 설정과 환경별 값을 나누고 수동 override에만 중요한 값이 남지 않게 한다.
- 배포: 같은 종류의 사전 검사와 배포 후 중앙 수집 검증을 적용한다.
- 환경별 값: IP, 도메인, 비밀값, 자원 제한, 보존 기간, 인스턴스 크기는 필요한 차이를 유지한다.
- 개발의 컨테이너 전환은 포트 충돌·SSE·로그 경로까지 개발에서 검증한 뒤 운영에 공통 구조를 반영한다.

## 서버와 앱 대시보드 확장 계획

사용자 경험, 처리 병목, 서버 자원을 연결해서 볼 수 있도록 보완한다. 기존 레포의 대시보드를 재사용하고 실제 수집되는 항목부터 연결한다.

| 대시보드 | 필요한 지표 | 현재 상태 |
| --- | --- | --- |
| 서버 | CPU, 가용 메모리, 디스크, 수집 상태, Swap 읽기·쓰기, 부하 | 기본 다섯 패널 완료, Swap 분리·부하 등 보완 |
| 앱 | 요청량, 5xx, p95, JVM Heap·GC, DB 커넥션 사용·대기 | 기존 backend-overview 활용, 중앙 앱 수집 미연결 |
| 서비스 작업 | 업로드, 사진·영상 처리, ZIP 성공률·시간·대기열 | 기존 media-transfer 활용, 중앙 연결·보완 필요 |
| 로그 | 오류, Request ID, 배포 버전, Nginx·앱 연계 | 기존 logs 활용, 중앙 실제 로그 수집 미연결 |
| 중앙 자체 상태 | 수집 실패, 로그 전송 실패, 저장 증가, 자원 | 자체 호스트 관측 완료, 전반적인 상태·외부 장애 감지 보완 |

- p95는 요청의 약 95%가 해당 시간 이내에 완료됐다는 뜻이다. 적은 요청량에서는 표본 수와 함께 해석한다.
- CPU·메모리뿐 아니라 컨테이너별 메모리 제한·OOM·재시작도 추가 관측 대상으로 논의했다.
- 업로드 현재 지표는 URL 발급·완료 등록 응답의 수락·거절 건수다. 실제 스토리지 전송 성공률·고유 파일 수로 해석하지 않는다.
- 현재 미디어 처리 시간은 한 번의 시도 시간이다. 대기열·재시도까지 포함한 사용자 전체 대기 시간은 별도 보완한다.
- ZIP 작업은 성공·실패, 실행 수, 처리 시간, 시작 전 대기 시간을 활용한다.
- SSE는 활성 연결, 전송 실패, 재연결을 추가 후보로 잡았다.
- CPU 크레딧은 node_exporter 기본 호스트 지표와 달라 CloudWatch 확인·연동이 필요하다.
- 방 ID·파일 ID처럼 계속 늘어나는 값은 Prometheus 라벨보다 로그 추적에 사용한다. 수집 시계열 증가도 작은 중앙 서버의 비용이다.
- 계측 원칙: [Prometheus instrumentation](https://prometheus.io/docs/practices/instrumentation/), [Micrometer histogram과 percentile](https://docs.micrometer.io/micrometer/reference/concepts/histogram-quantiles.html).

## 남은 작업과 완료 기준

호스트 연결 완료를 전체 이전 완료로 간주하지 않는다. 데이터 보존을 유지하면서 개발에서 먼저 검증하고 운영으로 확장한다.

1. **서버 설정과 레포 차이 정리**
   - 수동 파일·임시 override·기본 Compose를 비교한다.
   - 최신 상태와 다른 README 설명, 중앙 dashboard provisioning 누락을 정리한다.
   - 검토 가능한 단위로 변경을 나누고 커밋·푸시·PR은 사용자 요청을 받아 진행한다.
2. **개발과 운영의 공통 구성 확정**
   - Nginx 실행 방식, 수집 경로, 로그, 설정 구조, 검증 절차를 맞춘다.
   - 개발 적용·검증 후 운영에 반영한다.
3. **앱 지표를 중앙으로 연결**
   - 다음 출발점은 개발 기존 Prometheus의 앱 scrape 주소·경로 확인이다.
   - 그 조회 명령까지 안내했지만 결과를 받기 전에 이번 정리로 마무리했다.
   - 개발 연결 후 운영 연결, 요청·오류·지연·JVM·DB 지표를 확인한다.
4. **실제 로그 중앙 수집**
   - Alloy에서 중앙 Loki로 보내는 경로와 환경 라벨을 구성한다.
   - 중앙 테스트용 24시간 보존을 실사용 정책으로 검토하고 저장 공간·수집 실패도 확인한다.
5. **서버와 앱 대시보드 및 알림 보완**
   - 환경 선택·서버 구분, 서비스 작업 지표를 연결한다.
   - 알림 발생·전송·복구와 중앙 자체 장애 탐지를 확인한다.
6. **중앙 전용 CI/CD와 재배포 검증**
   - 중앙 설정·대시보드를 앱 배포와 독립적으로 배포한다.
   - 개발·운영·중앙 모두 다음 배포 후 설정과 관측이 유지되는지 확인한다.
   - 개발 호스트 Nginx의 설치·비교 방식도 공통 구조에 맞춰 재검토한다.
7. **과거 자료 분석과 이전 마무리**
   - 기존 운영 데이터를 유지한 상태에서 분석을 끝낸다.
   - 과거 데이터 조회 범위와 보존·복원 방안을 정한 후 기존 중복 서비스를 제거한다.
8. **부하 검증과 인스턴스 조정**
   - 작은 사양에서 단계적으로 부하를 늘린다.
   - 응답 시간, 처리 완료 시간, 오류율, 큐 증가, Swap·GC·CPU 크레딧을 함께 본다.
   - 동시성 조정 등으로 해결 가능한지 확인하고 필요하면 스케일업한다.

## 이번 작업의 마무리 기준

중앙에서 세 서버의 호스트 지표를 조회하고, 운영 수집 경로의 허용·차단을 확인한 상태에서 작업을 일단락한다.

- 완료: 중앙 서버 기반, 웹 접속, 개발·운영 호스트 수집, 세 환경 대시보드, 서버별 연결 확인.
- 유지: 기존 운영 데이터 저장소와 기존 운영 Grafana, 현재 보존 정책.
- 미완료: 재배포 안정성 확정, 개발·운영 구조 통일, 앱·로그 중앙화, 중앙 CD, 실제 알림, 부하 검증.
- 별도 확인 필요: 과거 자료 전체 존재 여부, 데이터 백업·복원, 사용자 외부 API·SSE의 장기 안정성.
- 이번 문서는 상태 기록이다. 전체 시스템을 한 번에 재생성하거나 데이터 저장소를 정리하는 실행 지침으로 사용하지 않는다.
