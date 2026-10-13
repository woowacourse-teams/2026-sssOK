# 중앙 모니터링 분리 작업 인수인계

개발·운영의 호스트 지표, 앱 지표, 로그와 Alloy 전송 지표를 중앙에 연결했다. 현재는 기존 모니터링도 유지하는 병행 운영 단계다. 배포 독립성, 알림, 장애·부하 검증, 기존 구성 정리와 앱 서버 사양 최적화까지 마쳐야 원래 목표가 완료된다.

- 기준일: 2026년 10월 13일.
- 원본 채팅: [모니터링 인프라 구성 (3)](codex://threads/01a1343f-60e0-76d3-9135-5ffe50264c69). 처음 이슈 계획부터 중단된 마지막 정리 요청까지 211개 턴을 조회했다.
- 관련 이슈: [#468](https://github.com/woowacourse-teams/2026-sssOK/issues/468).
- 이 문서는 원본 채팅의 서버 실행 결과·사용자 확인과 현재 로컬 파일을 대조한 기록이다. 이번 정리에서 실제 서버에 재접속하거나 재배포하지 않았다.
- 초기 구축 과정과 개념 설명은 [기존 작업 기록](CENTRAL_MONITORING_WORK_LOG_2026-10-13.md)에 남아 있다. 그 문서의 완료 상태는 호스트 수집 단계 기준이다. 최신 상태와 다음 작업은 이 문서를 우선한다.

## 원래 목표

개발·운영 서버마다 앱과 모니터링 전체가 함께 실행돼 자원을 경쟁했고, 앱 서버가 꺼지면 해당 서버의 모니터링도 사용할 수 없었다. 환경마다 설정과 배포를 관리하는 중복도 있었다.

1. Prometheus·Loki·Grafana·Alertmanager의 저장·조회·알림 부담을 별도 중앙 서버로 옮긴다.
2. 개발·운영에는 앱·Nginx·Alloy·node_exporter·Docker socket proxy를 남긴다.
3. 개발·운영·중앙을 환경과 서버 라벨로 구분해 관측한다.
4. 중앙 배포를 앱 배포와 분리한다. 중앙 장애가 API와 앱 배포를 막지 않아야 한다.
5. 기존 운영 지표·로그를 보존하면서 순차 전환한다.
6. 관측과 부하 시험으로 가장 작은 적정 사양을 찾고 개발부터 검증한 뒤 운영에 적용한다.
7. 중앙 서버까지 포함한 전체 비용과 성능, 설정 조정 및 증설 기준을 기록한다.

사용자는 큰 인스턴스를 먼저 확보하기보다 작은 사양에서 성능을 최대한 끌어내고, 측정으로 부족함이 확인될 때 키우는 방향을 선택했다. 개발 small·운영 medium을 무조건 micro로 내리기로 확정한 것은 아니다. 중앙 역시 micro를 유지하고 시험 후 증설을 판단하기로 최종 결정했다.

## 진행 방식과 유지 조건

- 한 단계씩 진행한다. 무엇을 하는지, 필요한 개념, 실행할 서버와 경로, 명령, 성공 기준을 함께 설명한다.
- 실제 서버 작업은 주로 사용자가 AWS 콘솔 Session Manager에서 실행하고 결과를 전달했다. 로컬 수정과 서버 적용을 구분한다.
- 설정 검사는 실제 수집 성공과 다르고, 서버 수동 적용은 CI/CD 배포 완료와 다르다.
- 운영 과거 자료는 기능의 목표·가설·지표 분석에 필요한 자산이다. 분석 완료를 확인하기 전 기존 저장소를 제거하지 않는다.
- 기존 볼륨 유지가 무기한 보관이나 백업 완료를 뜻하지 않는다. 기존 보존 정책에 따른 자동 삭제는 계속된다.
- 일괄 재생성, 볼륨 삭제, `down -v`, 불필요한 리소스 정리를 진행하지 않는다.
- 커밋·푸시·PR 생성은 별도 사용자 요청에 따라 진행한다. 이번 정리는 해당 작업을 수행하지 않는다.

## 서버와 네트워크

| 구분 | 사설 IP | 인스턴스 | 서브넷 | 작업 경로 |
| --- | --- | --- | --- | --- |
| 중앙 | 10.0.20.46 | t4g.micro | project-app-a | /home/ubuntu/monitoring |
| 개발 | 10.0.0.69 | t4g.small | project-public-a | /home/ubuntu/app |
| 운영 | 10.0.20.127 | t4g.medium | project-app-a | /home/ssm-user/app |

- 모두 TECHCOURSE-PROJECT VPC, 서울 리전, ap-northeast-2a에 있다. 개발·운영은 이전에 모두 t4g.micro였다.
- 중앙은 Ubuntu 24.04.4 LTS ARM64, 20GiB gp3, Swap 1GiB로 시작했다.
- 중앙 공인 IP는 할당하지 않았다. 관리 접속은 Session Manager, 외부 다운로드는 기존 NAT, Grafana 접속은 기존 ALB를 이용한다.
- 서버 간 수집은 사설 IP와 VPC local 경로를 사용한다. NAT를 거치는 수집 구성이 아니다.
- 조직 규칙에 맞춰 중앙은 project-app 보안 그룹과 ec2-project IAM 역할을 사용하도록 구성했다. 신규 전용 보안 그룹 제안은 채택하지 않았다.
- project-app self 규칙은 같은 그룹의 서버끼리 넓게 통신을 허용한다. 중앙만 접근할 수 있다는 보장은 Nginx의 별도 원본 IP 제한으로 보완했다.
- 조직의 인스턴스 상한은 t4g.medium이고 ALB에는 지정된 WAF를 유지하는 조건이다.

## 현재 데이터 흐름

| 데이터 | 현재 경로 |
| --- | --- |
| 개발·운영 호스트 지표 | 중앙 Prometheus → 각 서버 사설 IP:19100/metrics → node-exporter:9100/metrics |
| 개발·운영 앱 지표 | 중앙 Prometheus → 각 서버 사설 IP:19100/app-metrics → app:8081/actuator/prometheus |
| 개발·운영 Alloy 상태 | 중앙 Prometheus → 각 서버 사설 IP:19100/alloy-metrics → alloy:12345/metrics |
| 개발·운영 로그 | Alloy → 기존 loki:3100 및 중앙 10.0.20.46:13100 → 중앙 Loki |
| 중앙 자체 상태 | 중앙 Prometheus → 중앙 node_exporter·Prometheus·Loki·Grafana·Alertmanager |
| 중앙 화면 | 브라우저 → 기존 ALB → 중앙 Nginx:80 → Grafana:3000 |

- 수집·평가 주기는 15초다. 현재 설정은 중앙 5개 대상과 개발·운영 각각 3개 대상을 포함한다.
- 저장 시계열 라벨은 개발 `environment=dev, server=dev`, 운영 `environment=prod, server=prod`, 중앙 `environment=monitoring, server=monitoring`이다.
- 앱 로그 라벨은 `service=sssok-backend`, Nginx는 `service=sssok-nginx`다. `sssok-app`으로 검색하면 안 된다.
- Alloy 전송 지표의 `host`는 발신 서버가 아니라 목적지다. `loki:3100`과 `10.0.20.46:13100`을 구분한다.
- 현재 Loki는 단일 테넌트다. 초기에 제안했던 dev·prod 멀티테넌트 분리는 구현하지 않았다. 환경별 보존은 라벨로 구분하며, 환경별 독립 수집량 제한은 남아 있다.

## 완료한 작업과 검증

| 작업 | 마지막 기록의 상태 | 확인 범위 |
| --- | --- | --- |
| 중앙 OS·Docker·Compose·Swap | 완료 | 재부팅 후 Docker와 Swap 자동 활성화, ARM64 실행 확인 |
| 중앙 5개 모니터링 서비스 | 완료 | 기동, 자체 지표 수집, 테스트 로그 저장·조회 |
| 중앙 Grafana 웹 접근 | 완료 | 로그인과 /central/ 접속, health 200 |
| 세 서버의 호스트 관측 | 완료 | CPU·메모리·Swap·Load·디스크와 up 확인 |
| 개발·운영 앱 지표 | 완료 | 중앙 JVM 지표 조회, Prometheus dev·prod up=1 확인 |
| 개발 Docker Nginx 전환 | 완료 | API UP, 호스트·앱 수집 유지, 정식 Nginx 로그 조회 |
| 개발·운영 로그 이중 전송 | 기본 검증 완료 | Nginx 같은 Request ID를 기존·중앙 각각 조회 |
| 앱 로그 중앙 수신 | 완료 | dev·prod 각각 최근 로그 5건 조회 결과 확인 |
| 중앙 로그 수신 접근 제한 | 시험 완료 | 허용 서버 전송 성공, 비허용·위조 XFF 요청 403 |
| 호스트·앱 지표 경로 접근 제한 | 시험 완료 | 중앙 접근 성공, 기존 시험 경로에서 비허용·위조 요청 403 |
| Alloy 지표 중앙 접근 | 완료 | 개발·운영 모두 목적지별 전송 카운터 조회 |
| Alloy 정기 수집 | 사용자 완료 확인 | dev·prod up=1 확인 안내 후 사용자가 완료 확인. 최종 원문 출력은 없음 |
| 새 Alloy 경로 비허용 접근 차단 | 결과 대기 | 두 서버 각각 일반·위조 요청, 총 4개 403을 확인하도록 안내한 뒤 중단 |
| 대시보드 4개 | 파일 작성·수동 Import 진행 | 사용자의 다음 단계 진행 확인은 있지만 전체 패널 실데이터 전수 확인은 아님 |

### 중앙 기본 구성

- Docker 29.9.0·Compose 5.6.0은 당시 설치 확인값이다.
- OS 업데이트·재부팅, ubuntu Docker 권한, 배포 폴더와 secrets 폴더 준비를 완료했다.
- Docker json-file 로그를 파일당 10MB·3개로 순환하도록 설정했다. Loki 로그 보존과 다른 설정이다.
- 중앙 메모리 한도는 Prometheus 192MiB, Loki 256MiB, Grafana 256MiB, Alertmanager 64MiB, node_exporter 48MiB, Nginx 32MiB다.
- Prometheus 보존은 15일·2GB 제한이다. 시간만으로 15일치 전체 보존을 보장하지 않는다.
- Grafana 익명 접속·회원가입은 비활성화했다. admin 초기 비밀번호는 중앙 .env에서 관리하며 인수인계 문서에 복사하지 않는다.
- 중앙 기본 점검 `check.sh`에서 준비 상태, 5개 자체 대상, 호스트 지표, Loki 저장·조회 성공을 확인했다.

### 웹 접근

- 중앙은 [monitor.ssssok.com/central/](https://monitor.ssssok.com/central/), 기존 운영은 [monitor.ssssok.com/](https://monitor.ssssok.com/)이다.
- 기존 sssok-alb·인증서를 재사용했다. 새 monitor-central 도메인·인증서 제안은 취소했다.
- 중앙 대상 그룹 이름은 sssok-monitoring-central, HTTP 80, 상태 검사 /health다.
- monitor 호스트와 /central 또는 /central/* 경로를 함께 만족하면 중앙으로 보낸다. 기존 monitor 규칙보다 앞선 우선순위를 안내했고 브라우저 접속을 확인했다.
- 중앙 Grafana의 ROOT_URL·하위 경로 설정, 별도 로그인 쿠키 이름, secure cookie, WebSocket 전달을 구성했다.
- 중앙을 관리할 때는 `docker compose -f compose.yml -f compose.web.yml` 두 파일을 함께 사용한다.

### 호스트 수집과 개발 Nginx 전환

- node_exporter는 원래 filesystem만 활성화돼 있었다. cpu·meminfo·vmstat·loadavg, 호스트 proc·sys·PID·읽기 전용 마운트를 추가했다.
- 초기 개발 호스트 Nginx는 중복 API 설정을 하나로 정리하고 loopback 9100·8081을 통해 지표를 전달했다.
- 이후 개발·운영 실행 방식 통일 요청으로 개발 Nginx를 Docker로 전환했다.
- 18080·19101 임시 포트에서 API·요청 ID·지표·접근 차단·Loki 로그를 시험했다.
- /etc/nginx를 백업하고 호스트 Nginx를 inactive·disabled로 전환한 뒤 정식 Docker Nginx가 80·19100을 받도록 했다.
- 전환 직후 한 번 connection reset이 있었지만 재시도 후 API UP을 확인했다. 정식 로그와 중앙 수집 유지도 확인했다.
- 시험용 컨테이너는 종료·제거했고 설정 파일은 참고용으로 남겼다.
- SSE는 buffering off·3600초 timeout 등 기존 설정을 유지했지만 사용자가 복잡한 실검증을 생략하도록 요청했다. **SSE heartbeat·장기 연결은 미검증**이다.
- 개발 8080 공개 연결과 loopback 8081·9100은 전환·복귀용으로 남아 있다. 안정화 후 정리 대상으로 검토한다.
- 운영은 완성된 nginx.conf를 마운트하고 개발은 default.conf.template을 시작 시 변환한다. Docker 실행 방식만 통일됐고 공통 설정·배포 절차 통일은 미완료다.

### 로그 수신과 이중 전송

- 중앙 Nginx 사설 13100에서 개발·운영 실제 접속 IP만 허용한다. POST /loki/api/v1/push만 전달하고 다른 경로는 차단한다.
- 실제 원본 주소 `$realip_remote_addr`로 판단한다. X-Forwarded-For에 허용 IP를 적는 것으로 우회되지 않는지 시험했다.
- 중앙 Loki 보존은 dev 168h, prod 720h, 기타 24h로 변경·검사·재시작했다. 기간 경과 후 삭제 동작은 아직 검증하지 않았다.
- `reject_old_samples_max_age=24h`는 신규 수신 로그의 나이 제한이며 저장된 로그의 보존 기간과 다르다. 과거 자료 이관은 하지 않았다.
- 기존 Loki endpoint를 남기고 중앙 endpoint를 추가했다. 중앙 timeout 5초, backoff 500ms~2초, 최대 재시도 3회로 제한했다.
- 두 목적지는 완전히 독립적이지 않다. 중앙 장애가 기존 전송에 주는 영향과 재시도 소진 후 누락은 별도 장애 시험이 필요하다.
- 개발 Nginx 같은 요청이 두 Loki에서 1건씩 확인됐다. 운영도 같은 확인에 대해 사용자가 완료했다.
- 앱 로그는 잘못된 service 라벨을 바로잡은 뒤 dev·prod 각각 최대 5건 조회에서 5건을 확인했다.

### Alloy 관측

- 개발 출력에서 기존·중앙 sent entries는 각각 671건, 표시된 retries·dropped는 0이었다. 204 배치 요청 수 220·221은 로그 건수와 다른 값이다.
- 운영 내부 조회에서 양쪽 3,887건, 표시된 retries·dropped 0을 확인했다.
- 운영 /alloy-metrics가 404여서 파일과 컨테이너 설정을 확인했고 경로 미추가를 확인했다. 경로 추가·reload 후 중앙 조회에서 양쪽 6,458건을 확인했다.
- Prometheus alloy job에 dev·prod를 모두 등록했다. 최신 로컬 설정도 두 대상을 포함한다.
- 누적 건수의 일치는 해당 시점의 증거다. 전체 로그 무손실 보장은 아니며, 앞으로 rate·increase로 최근 전송·재시도·폐기를 관찰해야 한다.

## 대시보드

| 파일 | 화면 | 구현 내용과 남은 확인 |
| --- | --- | --- |
| backend/monitoring/central/host-overview.json | sssOK Host Overview | 5→10패널. 수집 상태, CPU, iowait·steal, CPU 대비 Load, 메모리 비율·용량, 디스크 비율·용량, Swap 점유·입출력 |
| backend/monitoring/central/backend-overview.json | sssOK Central Backend Overview | 6→12패널. 요청·오류·p95, API별 지연, 상태 코드, Heap·Non-Heap, GC 평균 정지, 스레드, DB 연결·획득 시간 |
| backend/monitoring/central/logs.json | sssOK Central Logs | 최근 로그·WARN/ERROR·Request ID 추적 3패널. 환경·서비스·수준 선택, 추적은 앱과 Nginx를 함께 검색 |
| backend/monitoring/central/media-transfer.json | sssOK Central Upload & Download | 기존 화면 재사용. 업로드 수락·거절, 사진·영상 처리, 회수 배치, ZIP, 실행기·큐 |

- Host 13개, Backend 15개, Media 52개 조회식 문법 검사와 Logs JSON 구조 검사 통과 기록이 있다.
- Logs는 기본 최근 1시간, 패널당 최대 100줄, 자동 갱신 끔으로 구성했다. Media 기본 갱신은 60초다.
- 일반 HTTP 지표에서 health·Actuator·장기 SSE를 제외해 해석하도록 구성했다.
- 요청이 없는 기간의 p95·비율 공백은 0이나 정상의 증거가 아니다. 지표 자체 미노출과도 구분한다.
- 업로드 수락은 실제 S3 업로드 완료가 아니고, 미디어 시간은 1회 처리 시도이며 전체 대기·재시도 시간을 포함하지 않는다. ZIP 준비 성공은 사용자의 다운로드 완료가 아니다.
- 대시보드는 수동 Import 방식이다. dashboards.yml provider 파일은 있지만 중앙 Compose에 provider·JSON 마운트가 연결되지 않았다.
- **아직 없는 것:** 로그 전송·모니터링 자체 상태 화면, 서비스 전체 상태 화면, 컨테이너 한도·재시작·OOM 관측, 외부 API 검사.

## 자원 측정과 데이터 보존

| 시점의 관측 | 개발 | 운영 |
| --- | --- | --- |
| 초기 가용 메모리 | 약 412MiB | 약 1.7GiB |
| 앱 컨테이너 사용량 | 약 631MiB | 약 1.03GiB |
| 중앙으로 옮길 4개 서비스 합계 | 약 395MiB | 약 419MiB |
| 디스크 사용률·여유 | 53%·8.7G | 60%·7.4G |
| Prometheus·Loki·Grafana 볼륨 합계 | 약 734MB | 약 870MB |

- 이는 서로 다른 시점의 순간 관측이다. 부하 시험이나 최소 필요 사양을 뜻하지 않는다.
- 같은 앱도 JVM MaxRAMPercentage=75 설정, 서버 메모리, 요청·캐시·GC·버전에 따라 사용량이 달라질 수 있다. 단일 원인을 확정하지 않았다.
- 중앙의 이후 측정은 Grafana 245.3/256MiB, Loki 83.57/256MiB, Prometheus 75.21/192MiB, 가용 메모리 244MiB, Swap 사용 320MiB였다. 당시 운영 로그 연결 전 측정이므로 현재 최종 부하의 기준값으로 재사용하지 않는다.
- 해당 짧은 vmstat 구간에는 지속적인 Swap-out이 없었다. Grafana inspect는 running, restart=0, oom=false였다. 과거 모든 OOM 부재를 증명하지는 않는다.
- small 증설을 권했던 답변 뒤 사용자가 micro 유지·부하 시험 후 증설을 선택했다. **최종 결정은 micro 유지**다. Grafana 256MiB 한도와 서버 전체 용량을 따로 판단한다.
- 기존 Prometheus는 dev·prod 모두 15일, Loki는 dev 7일·prod 30일이다.
- 운영 app_prometheus-data, app_loki-data, app_grafana-data 볼륨을 유지한다. Alloy 수집 위치 볼륨도 유지했다.
- 과거 데이터의 중앙 복사, 전체 무결성 확인, 새 데이터 백업·복구 시험은 하지 않았다. 설정 파일 백업을 데이터 백업으로 간주하지 않는다.

## 문제 해결과 다시 혼동하면 안 되는 점

| 문제 | 확인한 원인 또는 결론 |
| --- | --- |
| 개발 up=0·Nginx 502 | 수집기는 Up이었지만 호스트 9100 매핑이 빠짐. 임시 override가 실제 실행 구성에 포함되지 않았음. 재적용 후 복구하고 기본 Compose·검증을 보강 |
| 개발 설정 명령이 경로 오류 | 중앙 서버에서 개발 경로 명령을 실행. 서버 IP·작업 디렉터리를 매 단계 표시해야 함 |
| 복원 명령까지 실행 | 정상 절차와 비상 복원 절차를 함께 안내해 혼동. 순차 명령과 복원 설명을 구분해야 함 |
| 로그 양쪽 0건 | 임의 Request ID가 앱의 소문자 UUID 검증에 걸려 교체됨. 응답의 실제 ID로 재검색해 확인 |
| 앱 로그 0건 | service=sssok-app을 잘못 사용. sssok-backend로 수정 후 조회 성공 |
| 운영 Alloy 404 | /alloy-metrics 경로가 서버 파일·컨테이너 설정 모두에 없었음. 추가·reload 후 해결 |
| Grafana 접속 후 빈 화면 | 데이터소스 연결과 대시보드 등록은 별도. JSON 수동 Import로 해결 |
| 로컬 AWS CLI 인증 실패 | InvalidClientTokenId, 브라우저 Bad Request. IAM 권한을 확인할 수 없었고 정확한 원인은 미확정. ALB 접속 경로로 진행 |

- 지표의 up=1은 수집 성공이다. 외부 사용자 API 성공과 같지 않다.
- 컨테이너 Up은 프로세스 실행이며, 데이터 저장·전송 성공은 별도 확인한다.
- Nginx `nginx -T`는 현재 파일 내용 검사·출력이며 실행 프로세스가 reload한 상태까지 보장하지 않는다.
- 개발 템플릿은 컨테이너 시작 시 변환된다. 템플릿만 수정하고 reload하면 변경이 적용되지 않을 수 있다. 포트 매핑 변경도 컨테이너 재생성이 필요하다.
- 운영 ALB realip 신뢰 대역은 기존 설정을 유지했다. 개발 템플릿을 그대로 운영에 복사해서 통일하면 안 된다.
- 초기의 직접 8081·9100·12345 노출, 멀티테넌트, 새 인증서, 즉시 small 증설은 현재 확정 구성이 아니다.
- VPC·서브넷·라우팅을 설명하는 GitHub Discussion용 글은 대화에서 초안으로 작성했다. 게시 완료 기록은 없다.

## 저장소 상태

2026년 10월 13일 이 인수인계 작성 직전 확인한 상태다.

- 브랜치: `chore/be-#468-central-monitoring-instance`.
- HEAD: `b447bf33` — `chore(backend): #468 개발 호스트 지표 수집 설정 고정`.
- 이전 커밋: `fd684928` — `chore(backend): #468 중앙 모니터링 초기 검증 구성 추가`.
- 두 커밋은 원본 채팅에서 커밋·푸시 완료 기록이 있다. 그 이후 변경은 미커밋이다. 원격의 현재 PR·CI·머지 상태를 이번에 새로 확인한 것은 아니다.
- `.github/backend-version-intents/468.txt`는 `patch`다.
- 인수인계 작성 전 tracked 변경 12개, +304/-110줄이었다. 신규 파일은 이 diff 통계에 포함되지 않는다.

수정된 기존 파일:

```text
.github/workflows/backend-ci.yml
.github/workflows/deploy-dev.yml
backend/docker-compose.dev.yml
backend/docker-compose.prod.yml
backend/monitoring/alloy/config.alloy
backend/monitoring/central/README.md
backend/monitoring/central/dev.nginx.conf
backend/monitoring/central/loki.yml
backend/monitoring/central/prometheus.yml
backend/monitoring/check-dev-node.sh
backend/nginx.conf
backend/src/main/resources/application.yml
```

원래 채팅에서 추가한 미추적 파일:

```text
backend/monitoring/central/backend-overview.json
backend/monitoring/central/check-dev.sh
backend/monitoring/central/compose.web.yml
backend/monitoring/central/dashboards.yml
backend/monitoring/central/host-overview.json
backend/monitoring/central/logs.json
backend/monitoring/central/media-transfer.json
backend/monitoring/central/nginx.conf
backend/monitoring/nginx/container/compose.preview.yml
backend/monitoring/nginx/container/default.conf.template
backend/monitoring/validate-dev-node.sh
docs/deployment/CENTRAL_MONITORING_WORK_LOG_2026-10-13.md
```

이번 정리에서 이 인수인계 파일을 추가하고 기존 작업 기록 맨 위에 최신 상태 안내를 추가했다. 서비스 설정 변경은 하지 않았다.

### 배포 반영의 현재 한계

- backend-ci.yml에 개발 관측 검증 job과 최종 결과 연계를 추가했다.
- deploy-dev.yml은 이제 호스트 Nginx 비활성 확인, 템플릿 복사·검사, Docker Nginx 재생성·API 확인을 포함한다. 초기의 호스트 Nginx 파일 비교 방식 설명은 더 이상 최신이 아니다.
- 개발 로컬 점검, 구성 정적 검사, 중앙 dev 필수 지표 확인 스크립트가 있다. 누락·실패를 잡는 로컬 시험 기록도 있다.
- 실제 GitHub CI와 후속 재배포 검증은 미완료다. 운영과 중앙까지 동일한 검증을 확장해야 한다.
- deploy-prod.yml은 기존 파일 복사·Nginx 검사/reload·Grafana 확인 흐름이 남아 있다. 이번 변경으로 배포 독립성을 완성한 것은 아니다.
- 공통 Alloy 파일에 중앙 사설 주소가 하드코딩돼 있다. 로컬 환경도 사용하는 파일이므로 주소와 중앙 전송 여부를 환경별로 관리해야 한다.
- 중앙 전용 CI/CD 워크플로·배포 러너 구성은 미구현이다. 초기 구상은 검증된 커밋을 지정하는 수동 중앙 CD다.
- 서버별 최종 파일과 로컬 원본의 완전한 동일성은 재확인해야 한다.

## 정확한 중단 지점과 첫 확인

### 후속 확인 (2026-10-13)

아래는 최신 순서의 기록이다. 과거 항목의 미완료 상태보다 최신 확인을 우선한다.

- GitHub Environment `monitoring`, 환경 변수 `DEPLOY_PATH=/home/ubuntu/monitoring` 생성 완료(사용자 확인).
- 중앙 수동 CD를 로컬 저장소에 구현했다. develop에 포함된 전체 SHA를 GitHub-hosted 러너에서 검사하고, 같은 실행의 허용 파일 artifact를 중앙 전용 러너에 전달한다. `.env`는 서버 파일을 유지한다. 이미지 ID·볼륨·메모리 한도 변경을 거절하는 설정 전용 배포다.
- 이전 설정과 정지 상태의 Grafana 데이터 전체를 서버 `backups/`에 보관한다. 순차 재생성 후 기존 중앙 검사·Nginx 경로·자동 등록 5개를 확인하고 성공 SHA를 남긴다. 오류 시 설정·Grafana 데이터를 복원하고 이전 기본 검사를 수행한다. 취소·전원 장애·복구 자체 실패는 수동 조치가 필요하다. Prometheus·Loki 시점 백업은 별도 과제다.
- 로컬 검증: 복구 관련 단위 시험 5개, 워크플로 YAML·셸·Python 문법, 실제 격리 Grafana의 백업 → 자동 등록 → 재생성 → 이전 수동 대시보드 DB 복원 통과. 기존 미커밋 변경을 보존했으며 커밋·푸시·PR·실제 GitHub CI/CD·서버 적용은 하지 않았다.
- 다음 단계는 미커밋 변경을 검토 가능한 단위로 정리해 저장소에 반영하는 것이다. 커밋·푸시·PR은 사용자 요청을 받은 후 수행한다. 수동 워크플로는 기본 브랜치에 있어야 실행할 수 있으며, 최초 실행 때 중앙 서비스의 잠시 중단과 배포 후 dev/prod 전송 상태도 함께 확인한다. 중앙 micro·기존 운영 데이터 유지 방침은 그대로다.

- 중앙 러너 준비 완료(사용자 실행 결과): `/home/ubuntu/actions-runner-monitoring`에 Linux ARM64 러너를 설치하고 이름 `sssok-monitoring`, 추가 라벨 `monitoring`으로 등록했다. ubuntu 계정의 systemd 서비스가 enabled·active(running)이며 GitHub Idle과 라벨도 사용자가 확인했다. ubuntu의 Docker 접근(29.9.0), `/home/ubuntu/monitoring` 쓰기, 기존 `.env` 읽기 모두 OK다. 당시에는 중앙 CD와 GitHub 배포 environment 구성이 미완료였으며 후속 상태는 위 최신 기록을 참고한다.

- 사용자 확인: 중앙 GitHub Actions 러너는 아직 미설치다. 단계별 진행을 위해 중앙 CI부터 작성했다. `monitoring-central-ci.yml`은 중앙 경로 변경 PR·develop push·수동 실행을 GitHub-hosted 러너에서 검사한다. 중앙 CD·러너 설치·선택 커밋 배포·백업/실패 복구 연결은 다음 단계다.
- `central/validate.py`와 `central/test-dashboards.py` 추가. 같은 서비스 이미지로 Compose·설정·기존 알림 시험·대시보드 PromQL 90개와 JSON을 검사하고, 격리 Grafana에서 기존 UID 갱신·5개 자동 등록·재생성 보존을 확인한다. 로컬에서 모두 통과했다. 실제 GitHub 실행·서버 적용은 아직 없으며 LogQL 실행·실데이터 패널 검증은 포함하지 않는다.

- 중앙 대시보드 자동 등록을 로컬 Compose에 연결했다. provider와 Host·Backend·Logs·Media·Log Transfer JSON 5개를 읽기 전용으로 마운트한다. UID·데이터소스·`grafana-data` 볼륨·메모리 한도는 유지한다. provider는 파일 누락에 의한 DB 화면 자동 삭제를 막고(`disableDeletion: true`), 변경을 저장소에서 관리하도록 UI 저장을 막는다(`allowUiUpdates: false`).
- 격리된 Grafana 13.2.2에서 자동 등록 5개·sssOK 폴더·기존 수동 등록 UID/DB ID의 중복 없는 갱신·동일 임시 볼륨 재생성 후 보존을 API로 확인했다. 실제 패널 렌더링·서버 데이터 조회는 별도 검증 대상이다. 시험 컨테이너와 전용 임시 볼륨만 제거했다.
- 중앙 서버 적용은 아직 하지 않았다. 최초 적용 전에 현재 5개 화면의 JSON과 UID를 확인·보관해야 한다. 파일 내용이 기존 동일 UID 화면을 덮어쓰므로 데이터 볼륨 유지가 기존 편집 내용 보존을 보장하지는 않는다. 중앙 전용 CI/CD도 아직 미구현이다.

- 로컬 저장소에서 Alloy 전송 설정 환경 분리 완료: 공통 `config.alloy`와 환경별 `write.local.alloy` 또는 `write.dual.alloy`를 `/etc/alloy/config/`에 두 파일만 마운트한다. local은 기존 Loki만, dev·prod는 기존 Loki와 `CENTRAL_LOKI_URL`로 전송한다. 중앙 URL 기본값은 기존 주소다. component ID와 수집 위치 볼륨·storage.path는 유지했다.
- CI는 세 환경의 실제 Compose 선택·환경변수·마운트·수집 위치 보존과 Alloy 유효성을 검사한다. CD는 공통·dual 파일을 함께 배치하고 새 디렉터리를 validate한다. 최초 적용은 실행 경로·마운트 변경으로 Alloy 재생성이 필요하며, 공통 파일 하나만 복사해 기존 프로세스를 reload하면 안 된다. 아직 서버에 적용하지 않았다.
- 검증: 세 환경 validate, 워크플로 YAML·셸 문법, 기존 개발 관측·Nginx 검사를 통과했다. 외부 통신·실제 볼륨 없는 임시 Alloy에 샘플 로그를 입력해 local 목적지 1개, dev·prod 목적지 2개와 기존 `loki.write.loki` ID를 실제 지표로 확인했다. 이는 서버의 로그 수신·무손실 검증이 아니다. 임시 컨테이너는 종료했다.

- 로컬 CI/CD 보강: 개발 사후 검사에 Alloy 경로 차단 추가, 운영 `check-prod-node.sh`를 배포에 연결했다. 운영은 Nginx 컨테이너에서 내부 호스트·앱·Alloy 지표를 읽고 사설 19100의 일반·위조 요청 차단을 검사한다. 중앙 수집·로그 수신 성공을 대신하는 검사는 아니다.
- 개발·운영 배포에서 파일 배치 전 `validate-alloy.sh`로 해당 Compose의 Alloy 이미지 버전과 환경을 사용해 후보 설정을 검사한다. 앱 헬스체크 이후 실행 중인 Alloy에서도 validate하고, Nginx 컨테이너를 통해 내부 `POST /-/reload` 응답을 확인한다. 실패 시 정상 앱을 자동 롤백하지 않고 배포 실패로 보고한다. 사전 문법 검증은 모든 실행 오류나 로그 수신 성공을 보장하지 않는다.
- 로컬 검증: dev·prod Alloy v1.20.1 validate 통과, 격리 컨테이너의 HTTP reload 성공·잘못된 설정 거절·거절 후 프로세스 유지 확인, 관련 워크플로 YAML·셸 문법 검사 통과. 실제 GitHub CI/CD·서버 적용은 미실행이다.
- 운영 사용자 제공 파일 비교: Compose는 주석 한 줄 차이, Nginx는 주석·블록 순서 차이이며 동작 설정은 동일했다. Alloy 전송 목적지·재시도 설정도 동일하나 채팅 이스케이프 변환 때문에 정규식의 바이트 단위 동일성은 미확인이다.

- 17:29 KST 개발 node_exporter 기본 Compose 통합 검증 완료(사용자 실행 결과). 서버 기본 파일에 기존 override의 수집 옵션·PID·마운트·loopback 포트를 통합하고 Compose 검사 후 node_exporter만 재생성했다. inspect에서 `running`, 기본 `docker-compose.dev.yml` 단독 사용, `pid=host`, 기존 수집 옵션과 포트 유지를 확인했다. 중앙에서 dev node `up=1`, CPU 시계열 16개·가용 메모리 시계열 1개를 확인했다. 백업과 override 파일은 보존했다. 실제 CI/CD 재배포 검증 완료를 뜻하지 않는다.
- 사용자 제공 개발 서버 파일 비교: Nginx 템플릿과 Alloy는 동작 설정이 저장소와 같고, Compose의 실제 차이는 node_exporter 기본 설정 누락이었다. 위 통합으로 해당 누락을 보완했다. 주석·배치 차이가 있으므로 파일 해시의 일치까지 확인한 것은 아니다.

- 이어받은 채팅에서 사용자가 개발·운영 각각 일반·위조 XFF 요청 총 4개의 HTTP 403을 확인했다.
- 중앙 Grafana에서 `up{job="alloy"}`의 개발·운영 값이 모두 1임을 사용자가 확인했다.
- 따라서 아래 접근 제한 시험은 완료했다. 에이전트의 직접 서버 조회가 아닌 사용자 실행 결과 확인이다.
- 다음 작업은 로그 전송 상태 대시보드다. 실제 노출된 지표 이름과 목적지 라벨을 확인한 뒤 조회식을 구성하고 실데이터를 검증한다.
- 17:13 KST 사용자 제공 조회 결과: sent entries는 dev 기존·중앙 각각 2,321건, prod 각각 6,753건. dropped entries는 네 조합 모두 0. 앞선 17:12 조회에서 양 환경·양 목적지 batch retries도 0이었다. 누적 카운터의 순간값으로 전체 무손실을 보장하지 않는다.
- `backend/monitoring/central/log-transfer.json` 추가: 수집 상태, 최근 5분 전송·재시도·폐기·응답 코드·폐기 사유, 전송 속도, 누적 카운터 10패널. 기본 1시간·60초 갱신. 중앙 Grafana Import와 실데이터 확인은 아직 대기 중이다.

운영 /alloy-metrics 추가와 중앙 조회, Prometheus의 운영 Alloy 등록까지 진행하고 사용자가 완료를 확인했다. **다음의 비허용 접근 시험을 안내했지만 결과를 받기 전에 채팅을 전환했다.** 운영 Alloy를 다시 처음부터 설치하거나 등록할 단계가 아니다.

개발 서버 10.0.0.69에서:

```bash
for forwarded in "" "10.0.20.46"; do
  curl --noproxy '*' --max-time 5 -sS \
    -o /dev/null -w "개발 XFF='$forwarded': HTTP %{http_code}\n" \
    -H "X-Forwarded-For: $forwarded" \
    http://10.0.0.69:19100/alloy-metrics
done
```

운영 서버 10.0.20.127에서:

```bash
for forwarded in "" "10.0.20.46"; do
  curl --noproxy '*' --max-time 5 -sS \
    -o /dev/null -w "운영 XFF='$forwarded': HTTP %{http_code}\n" \
    -H "X-Forwarded-For: $forwarded" \
    http://10.0.20.127:19100/alloy-metrics
done
```

예상 결과는 네 번 모두 HTTP 403이다. 새 세션에서는 중앙의 `up{job="alloy"}`도 함께 읽어 dev·prod가 현재도 1인지 확인하면 된다. 이 확인은 설정 변경이 아니다.

## 남은 작업과 완료 기준

| 순서 | 작업 | 완료 기준 |
| --- | --- | --- |
| 1 | 새 Alloy 경로 접근 제한 마무리 | 위 4개 403과 중앙 수집 성공을 함께 확인 |
| 2 | 로그 전송·모니터링 자체 상태 대시보드 | 환경·목적지별 전송, 실패·재시도·폐기의 최근 증가량과 수집 상태 표시 |
| 3 | 현재 배포 구성 정리 | 서버·레포 대조, 공통/환경 설정 분리, 기존 임시·복귀 설정의 관리 목적 명확화 |
| 4 | 대시보드 자동 등록·실데이터 확인 | 재생성 후 화면 유지, dev·prod·monitoring별 해당 패널의 값·단위·공백 의미 확인 |
| 5 | 개발·운영 공통화와 CI/CD 마무리 | ALB·SSE·로그 의미를 보존하고 실제 재배포 뒤 지표·로그·접근 제한 유지 |
| 6 | 중앙 전용 CI/CD | 설정·규칙 검증, 선택 커밋 배포, 비밀값 관리, 백업·실패 복구, 앱과 배포 분리 |
| 7 | 빠진 수집과 전체 상태 화면 | 컨테이너 메모리/한도·CPU·재시작·OOM, 외부 도메인·TLS·ALB를 통과하는 API 검사 |
| 8 | 실제 알림 | dev·prod별 규칙·그룹 구분, Discord 발생·복구 알림, 중복 발송 없이 책임 전환 |
| 9 | 중앙 외부 감시 | 중앙 서버 전체 중단을 중앙 밖에서 감지 |
| 10 | 백업과 장애 시험 | 복원 가능성, 중앙 장애 때 기존 로그·API·배포 영향, 로그 누락·재시도, 앱 장애 때 과거 자료 조회 검증 |
| 11 | micro 부하 시험 | 평상시·수집·대시보드 조회·로그 검색·개발 로그 폭주를 단계적으로 증가시키며 수집/조회 품질 측정 |
| 12 | 데이터 분석과 기존 구성 정리 | 사용자 분석 완료·보존 범위·복구 방안을 확인한 뒤 중복 서비스 중지, 볼륨 보관 |
| 13 | 앱 서버 적정 사양 검증 | 분리 후 개발부터 JVM·동시성·큐를 조정해 부하 시험, 이후 운영에 검증된 설정 적용 |
| 14 | 비용과 결과 문서화 | 중앙 포함 EC2·EBS·백업·네트워크 비용, 감당 부하, 증설·복구 기준 정리 |

세부적으로 놓치지 말아야 할 항목:

- 중앙 알림 규칙은 현재 CentralComponentDown·CentralMemoryLow·CentralDiskSpaceLow의 초기 3개뿐이다. Alertmanager bootstrap receiver에는 실제 전송처가 없다.
- 기존 앱·로그 알림을 중앙으로 옮길 때 전체 합산 때문에 개발 장애가 운영 알림으로 섞이지 않도록 환경·서버별 집계와 테스트가 필요하다.
- 컨테이너 수집기는 cAdvisor 등을 검토했지만 설치하지 않았다. micro에 추가되는 수집 비용도 측정한다.
- node_exporter는 선택한 collector만 켜져 있다. 네트워크·디스크 I/O 등 추가 항목은 실제 필요와 부담을 보고 결정한다.
- t4g CPU 크레딧·초과 사용은 CloudWatch 지표다. 우선 콘솔 확인, 권한·비용 확인 후 Grafana 연동을 검토하는 수준이다.
- 미디어 등록부터 사용 가능까지 전체 시간, SSE 연결·실패·재연결은 추가 계측 후보다. 모두 이미 구현된 것으로 간주하지 않는다.
- 개발 로그 폭주에서 운영 수집을 보호할 방법과 환경별 수집량 제한은 미완료다. 현재 단일 테넌트의 전체 제한을 독립 제한으로 해석하지 않는다.
- 일반 요청 p95·오류율, 핵심 작업 완료 시간·큐, GC·Swap·OOM, 로그 폐기, 수집 실패·조회 지연을 함께 본다. 통과 임계값과 관찰 기간은 아직 확정하지 않았다.
- Grafana만 한도에 걸리면 컨테이너 제한·조회 설정을 검토한다. 서버 전체의 지속적 메모리 압박·Swap·품질 저하일 때 인스턴스 증설을 비교한다.
- 큰 변경을 하나의 PR로 밀어 넣지 말고 검토 가능한 관심사로 정리한다. 이슈 468 추적 키와 팀 브랜치·커밋·PR 규칙을 따른다.

## 새 세션 시작 문장

> 이슈 #468 중앙 모니터링 분리 작업을 이어서 진행하자. 이 인수인계 문서의 최신 후속 기록을 먼저 읽고 미커밋 변경을 보존해 줘. 접근 차단과 개발 node_exporter 통합을 확인했고, 로컬 저장소에 Alloy 환경 분리·대시보드 자동 등록·중앙 CI와 수동 CD를 준비했다. 중앙 전용 러너와 GitHub monitoring 환경·DEPLOY_PATH 변수도 준비 완료다. 실제 GitHub 실행·서버 반영은 아직 하지 않았다. 다음은 변경분을 검토 가능한 단위로 정리하는 것이다. 사용자 요청 없이 커밋·푸시·PR을 만들지 말고, 한 번에 한 단계씩 서버·목적·개념·성공 기준을 설명해 줘. 중앙은 micro 유지, 기존 운영 데이터는 분석용으로 보존한다.
