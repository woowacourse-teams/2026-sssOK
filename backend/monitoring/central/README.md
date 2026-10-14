# 중앙 모니터링 기동 검증 (#468, 1단계)

## 최신 서버 적용 상태 (2026-10-13)

[중앙 CD 첫 실행](https://github.com/woowacourse-teams/2026-sssOK/actions/runs/38416851150)이 성공했다.
적용 커밋은 `c9756cc61980af43ea84ba350f1d6636d27a5fb9`이며, 설정·Grafana 데이터 백업,
중앙 자체 수집 5개 대상, 호스트 지표, Loki 시험 로그 저장·조회, 대시보드 5개 자동 등록을 확인했다.
백업 경로는 `/home/ubuntu/monitoring/backups/20261013T145150.855227Z-c9756cc61980`이다.
기동 직후 준비 상태 조회의 503은 재시도 후 정상화됐다. 실패 복구는 이번 실배포에서 실행되지 않았다.

아래 추가 알림 규칙 15개는 후속 작업이며 이 첫 배포에 포함되지 않았다. 중앙 서버는 기존
기본 알림 규칙과 외부 발송 없는 receiver를 사용한다. 배포 후 개발·운영의 node/backend/alloy
6개 대상 수집, 실제 로그 전송과 모든 패널 값은 별도로 확인해야 한다.
과거 절의 수동 Import·서버 적용 전 설명보다 이 상태 기록을 우선한다.

## 중앙 알림 이전 준비 (#468, 발송 전)

중앙 규칙은 앱·로그·호스트 알림과 중앙 자체 상태를 포함한 15개다. 기존 개발·운영
규칙·발송 설정은 변경하지 않는다. 중앙 receiver `bootstrap`에는 전송 설정이 없으므로
서버의 Discord 선택 파일이 없으면 이번 구성을 배포해도 외부 알림은 보내지 않는다. 실제 Discord 연결과 기존 발송 중지는 후속 작업이다.

| 규칙 | 계산 단위·판정 |
| --- | --- |
| BackendDown | dev/prod 수집 대상별 up=0, 2분 (복구 후 5분 유지) |
| BackendHigh5xxErrorRate | 환경·서버별 최근 5분 요청 20건 이상, 5xx 비율 5% 초과 (복구 후 5분 유지) |
| HostDiskSpaceLow / Critical | dev/prod/monitoring 루트 파일시스템별 여유 15% 미만 10분 / 5% 미만 5분 |
| HostDiskWillFillIn24h | 같은 파일시스템의 6시간 추세로 24시간 내 고갈 예상, 여유 40% 미만 30분 |
| LogPipelineDown | dev/prod Alloy 수집 실패 5분 (복구 후 5분 유지) |
| LogShippingFailing | 환경·서버·목적지별 5분 실패 요청의 초당 발생량(rate)이 양수인 상태 10분 |
| LogEntriesDropped | 환경·서버·목적지·사유별 10분 폐기 증가량 > 0 |
| LokiRejectingLogs | 중앙 Loki·사유별 10분 거절 증가량 > 0 |
| NodeMetricsDown | dev/prod 호스트 지표 수집 실패 2분, 해소 후 5분 유지 |
| MetricsEndpointDown | 같은 환경·서버·instance의 node/backend/alloy 모두 수집 실패 2분, 해소 후 5분 유지 |
| CentralComponentDown | 중앙 구성 요소 수집 실패 2분 |
| HostHighCPU | dev/prod/monitoring 서버별 전체 코어 5분 평균 사용률 85% 초과가 10분 지속 |
| HostMemoryLow | dev/prod/monitoring 서버별 MemAvailable / MemTotal 10% 미만 5분 |
| BackendHighLatency | dev/prod 환경·서버별 5분 P95 > 1초, 같은 구간 요청 100건 이상이 5분 지속 |

오류율의 분자·분모·최소 요청 수 모두 같은 환경·서버로 묶는다. 기존 Actuator 제외 조건과
임계값을 유지한다. Alloy `host`는 발신 서버가 아니라 목적지다. 중앙 Loki의 거절 지표는
발신 환경을 제공하지 않으므로 monitoring으로 알리며 dev/prod로 추측하지 않는다.
전송 목적지 하나의 폐기가 다른 목적지에서도 유실됐음을 뜻하지 않는다.
`up=0`은 실제 서비스 중단 확정이 아닌 수집 실패다. 수집 대상 설정 자체가 빠진 경우의
누락 검사는 별도 과제이며, 시계열이 없다는 이유로 정상 0을 만들어내지 않는다.

중복 처리는 다음과 같다.

- 중앙 디스크 전용 규칙은 공통 호스트 규칙으로 대체한다.
- 중앙 Loki 중단은 CentralComponentDown이 담당하며 LogPipelineDown은 Alloy만 검사한다.
- 디스크 critical은 같은 대상의 low를, low/critical은 같은 대상의 예측 알림을 억제한다.
- 대상 비교에는 environment·server·instance·device·mountpoint를 사용한다. 필수 대상
  라벨이 비어 있는 알림을 억제 근거로 사용하지 않는다.
- 전송 실패와 실제 폐기는 둘 다 유지한다. 같은 환경·서버·instance의 호스트·앱·Alloy 수집이 모두 실패할 때만 대표 알림이 세 개별 수집 실패 알림을 억제한다. 원인은 단정하지 않는다.
- 알림 그룹은 environment·server·alertname·severity·host, 대기/변경/반복은 30초/5분/4시간이다.
  실제 복구 알림 발송(send_resolved)은 추후 Discord receiver를 연결할 때 설정한다.

기존 서버와 중앙 사이의 중복 발송은 inhibit 규칙으로 해결되지 않는다. 먼저 중앙에서 발송 없이
판정 결과를 비교하고, 검증된 개발 알림 묶음부터 기존 발송 중지 → 중앙 발송 활성화 순으로
담당을 이전한다. 전환 시각과 짧은 공백을 기록한다. 복귀 시 중앙 발송을 끄고 기존 발송을
다시 켠다. 운영은 개발 검증 후 진행한다. 중앙이 관측하지 못하는 기존 Loki 자체 알림은 유지한다.

기존 `validate.py`에서 promtool 가상 지표 시험과 실제 격리 Alertmanager 시험을 수행한다.
`test-alertmanager.py`는 외부 통신 없는 임시 컨테이너에 21개 가상 알림을 넣어 같은 대상의
억제·다른 환경/서버/디스크의 비억제·critical 복구 후 warning 재활성화를 확인한다.
규칙 시험은 개발 오류와 운영 트래픽 분리, 서버별 최소 요청 수, 발생 대기·복구,
전송 목적지·폐기 사유 유지, 중앙 귀속, 디스크 임계값·예측을 확인한다.
이 검증은 실제 장애 발생이나 Discord 메시지 수신 검증을 대신하지 않는다.

공식 기준: [Prometheus 규칙 시험](https://prometheus.io/docs/prometheus/latest/configuration/unit_testing_rules/),
[Alertmanager 그룹·억제 설정](https://prometheus.io/docs/alerting/latest/configuration/).

## 발송 없는 적용 후 점검

알림 규칙 PR을 develop에 병합한 뒤 해당 커밋으로 중앙 CD를 실행한다. 첫 배포 커밋
`c9756cc6`에는 새 규칙과 이 점검 도구가 없으므로 후속 배포 전에는 실행할 수 없다.
중앙 호스트에서 평가 주기(15초)가 지난 뒤 실행한다.

```bash
cd /home/ubuntu/monitoring || exit 1
python3 check-alerts.py
```

이 도구는 loopback Prometheus API를 GET으로 조회한다. 설정 변경·재시작·시험 알림 발송을
하지 않는다. 15개 규칙의 이름·중복·평가 상태와 dev/prod 6개 수집 대상의 up=1을 검사한다.
실제 장애로 pending/firing인 규칙은 대상 라벨과 함께 표시하며, 그 사실만으로 점검을 실패시키지
않는다. 적용 누락·평가 오류·수집 실패는 종료 코드 1로 보고한다. 자동 CD 성공 판정에는
연결하지 않아 앱 수집 장애를 이유로 중앙 설정을 자동 롤백하지 않는다.

추가로 확인할 사항:

1. 출력의 firing/pending 대상을 기존 Prometheus 판정과 비교한다. 임계값·집계 단위·평가 시간을
   함께 확인하며, 알림이 없다는 사실만으로 정상 동작을 확정하지 않는다.
2. 기존 Alertmanager의 발송은 유지하고 중앙 receiver는 bootstrap으로 비워 둔다.
   이 도구는 Alertmanager의 실제 receiver·억제 상태나 Discord 수신 여부를 검사하지 않는다.
3. 실제 로그 전송과 대시보드 값은 별도 확인한다. 새 규칙이 안정적으로 평가되는 것을 확인한
   후 개발 알림 묶음부터 발송 담당을 전환한다. 서버 장애를 일부러 유발하는 시험은 별도 진행한다.

## 중앙 전용 CI (2026-10-13)

`.github/workflows/monitoring-central-ci.yml`은 중앙 폴더·해당 워크플로 변경 PR,
develop 병합 또는 수동 실행 때 GitHub-hosted `ubuntu-latest`에서 검사한다.
실제 서버 접속·비밀값·자체 호스팅 러너는 사용하지 않으며 중앙 micro에서 검사를 돌리지 않는다.

- `validate.py`: 두 Compose 병합, Prometheus 설정·알림 규칙과 기존 규칙 시험,
  Alertmanager·Loki·Nginx 설정, 대시보드 JSON·UID·데이터소스 참조·PromQL 문법을 검사한다.
  Grafana 변수는 대표값으로 치환해 문법만 검사한다. LogQL 실행과 실데이터 의미 검증은 포함하지 않는다.
- `test-dashboards.py`: 같은 Grafana 이미지로 수동 등록 화면의 동일 UID 갱신, sssOK 폴더의
  5개 자동 등록, 같은 데이터 볼륨으로 재생성 후 UID·DB ID 보존 및 이전 DB 복원을 검사한다. 시험은 외부
  통신 없는 컨테이너와 고유한 임시 볼륨을 사용하며 해당 시험 자원만 정리한다.
- 로컬 재현: 저장소 루트에서 `python3 backend/monitoring/central/validate.py`,
  `python3 backend/monitoring/central/test-dashboards.py`,
  `python3 backend/monitoring/central/test-deploy.py`. Python 3·Docker·Compose가 필요하다.
  설정 검사 컨테이너에는 설정 파일만 읽기 전용으로 연결하며 실제 `.env`나 저장 볼륨은 연결하지 않는다.
- 경로 필터가 있는 별도 워크플로이므로 무관한 PR에는 실행되지 않는다. 저장소 필수 체크
  설정은 이번에 변경하지 않았다. 설정 검증 성공은 중앙 서버 배포 성공을 뜻하지 않는다.

## 중앙 수동 CD (첫 실행 완료)

사용자 확인: 중앙 `sssok-monitoring` 러너(ubuntu, Linux ARM64, `monitoring` 라벨)는
서비스로 실행 중이며 GitHub Idle, Docker 접근, 배포 폴더 쓰기, 기존 `.env` 읽기를 확인했다.
GitHub Environment `monitoring`과 해당 환경 변수 `DEPLOY_PATH=/home/ubuntu/monitoring`도 준비했다.

`monitoring-central-deploy.yml`은 수동 실행만 지원한다. 다음 절차는 저장소 반영 후 사용한다.

1. 워크플로가 기본 브랜치에 있어야 GitHub에서 수동 실행할 수 있다.
   [GitHub 수동 실행 문서](https://docs.github.com/en/actions/how-tos/manage-workflow-runs/manually-run-a-workflow).
   실행 브랜치는 develop 또는 main이며, 입력 `commit`은 develop에 포함된 전체 40자리 SHA다.
2. GitHub-hosted 러너가 해당 SHA를 checkout하고 설정·대시보드·복구 시험을 통과시킨다.
   허용 목록의 파일만 같은 실행의 artifact로 전달한다. `.env`는 포함하지 않는다.
3. 중앙 전용 러너가 `/home/ubuntu/monitoring`의 기존 6개 서비스·이미지 ID·메모리 한도·
   데이터 볼륨을 대조한다. 이미지 업그레이드나 볼륨 변경은 이 배포에서 거절한다.
   기존 Grafana 관리자 인증과 백업 공간도 확인한다.
4. `backups/<UTC 시각>-<SHA 앞 12자리>/`에 이전 설정을 저장하고 Grafana를 정지한 상태에서
   데이터 전체를 tar로 보관한다. Grafana DB에는 자격증명 관련 정보가 있으므로 백업 폴더는
   700, 데이터 tar는 600 권한이다. 기존 `.env`는 복사하거나 덮어쓰지 않는다.
5. 설정을 배치하고 Loki·Prometheus·Alertmanager·Grafana·Nginx를 순차 적용한다.
   Loki·Nginx는 파일 내용 또는 서비스 정의 변경 시 재생성하고 나머지는 강제 재생성한다.
   node_exporter는 Compose 변경이 있을 때만 재생성한다. 이미지 pull이나 볼륨 삭제는 하지 않는다.
6. 서비스 준비 상태·중앙 자체 수집 5개·호스트 지표·Loki 시험 로그 저장/조회·Nginx 경로·
   대시보드 자동 등록 5개를 확인한 뒤 `deployed-commit.txt`에 SHA를 기록한다.
   오류 시 이전 설정과 Grafana 데이터를 복원하고 이전 `check.sh`를 실행하며, 배포 결과는 실패다.

복원 전에 tar 전체를 읽어 DB 존재·경로·파일 종류를 검사하고, 백업 당시와 같은 쓰기 가능한
Grafana 데이터 볼륨인지 확인한다. 링크·특수 파일·하위 마운트가 있으면 자동 복원을 거절한다.
백업을 별도 임시 폴더에 먼저 추출한 뒤 볼륨 내용을 교체하므로 새 플러그인·숨김 파일·SQLite
보조 파일도 제거된다. 추출 공간으로 백업 원본 크기와 256MiB의 여유가 추가로 필요하다.
복사 도중 디스크 오류·전원 장애까지 원자적으로 복구하는 방식은 아니며 원본 tar는 보존한다.

검증: `test-deploy.py`는 성공 기록, 배포/헬스체크 실패 복구, 백업 실패, 이미지 변경 거절과
신규 파일 제거·권한 복원·잘못된 백업/볼륨 거절·추출 실패 시 데이터 보존을 확인한다. `test-dashboards.py`는 실제 격리 Grafana에서 같은 백업/복원
메서드를 사용해 수동 대시보드 → 자동 등록 → 재생성 → 이전 DB 복원을 확인한다.
로컬 복구 검증과 중앙 CD 첫 실행은 통과했다. 실제 서버에서 장애를 유발하는 복구 시험은 아직 하지 않았다.

배포 중 중앙 수집·조회와 Grafana 화면이 잠시 중단될 수 있다. Alloy의 제한된 재시도로 인해
중앙 로그 일부가 누락될 수 있어 배포 후 개발·운영 전송 지표도 확인한다. 기존 환경의 Loki와
운영 데이터는 유지한다. 중앙 micro와 서비스 메모리 한도는 바꾸지 않는다.

백업은 같은 서버 디스크에 있으며 자동 정리하지 않는다. 서버 전체 손실 대비 백업은 별도 과제다.
Prometheus·Loki 데이터 볼륨의 시점 복원은 이 스크립트 범위가 아니다. 작업 강제 취소·러너 종료·
전원 장애 또는 자동 복구 실패는 백업 경로를 확인한 후 수동 복구가 필요하다.
배포 성공 검사에는 ALB/TLS 외부 경로와 모든 실데이터 패널의 의미 검증은 포함하지 않는다.

## 대시보드 자동 등록 (2026-10-13)

로컬 저장소의 `compose.yml`에 provider와 아래 다섯 JSON의 읽기 전용 마운트를 연결했다.
첫 중앙 CD에서 5개 자동 등록을 확인했다. Grafana는 `sssOK` 폴더에 같은 UID의
대시보드를 등록·갱신한다. 기존 데이터소스 UID `prometheus`·`loki`와 `grafana-data` 볼륨은 유지한다.

| 파일 | UID |
| --- | --- |
| `host-overview.json` | `sssok-host-overview` |
| `backend-overview.json` | `sssok-central-backend` |
| `logs.json` | `sssok-central-logs` |
| `media-transfer.json` | `sssok-central-media` |
| `log-transfer.json` | `sssok-log-transfer` |

- 같은 UID의 기존 화면은 파일 내용으로 갱신된다. 최초 서버 적용 전에 기존 화면 JSON을
  내보내 수동 편집 내용을 보관·비교한다. UID를 수동으로 바꾼 화면은 별개이므로 먼저 확인한다.
- `allowUiUpdates: false`: 변경은 저장소 JSON에서 관리한다. 화면 편집 내용을 저장하려면
  JSON으로 내보내 저장소에 반영한다. 기존 DB를 덮어쓰는 것이므로 단순 재시작으로 이전 편집이 복원되지는 않는다.
- `disableDeletion: true`: 배치 파일이 빠졌다는 이유만으로 DB의 기존 대시보드를 자동 삭제하지 않는다.
  실제 제거가 필요하면 별도 정리한다. 잘못된 파일 내용의 덮어쓰기까지 막는 옵션은 아니다.
- 30초 간격으로 파일 변경을 확인한다. JSON 마운트는 `create_host_path: false`로 두어 파일
  누락 시 빈 디렉터리를 만드는 대신 기동을 실패시킨다.
- 중앙에는 `compose.yml`, `compose.web.yml`, `dashboards.yml`과 다섯 JSON을 함께 배치한다.
  대시보드만 수동 적용한다면 두 Compose를 함께 지정하고 `up -d --no-deps grafana`로
  Grafana만 재생성할 수 있다. 위 중앙 CD는 전체 설정을 배포하며 Loki·Nginx는 변경이 있을 때 재생성한다.
- 재생성 후 다섯 UID·폴더·데이터소스 연결과 실제 패널 값을 확인한다. 자동 등록 성공과
  로그·지표 쿼리의 실데이터 성공은 별개다. 중앙 micro와 서비스별 메모리 한도는 변경하지 않는다.

## 미디어 화면 중앙 연결 (2026-10-13)

`media-transfer.json`을 중앙 Grafana에 Import하면 `sssOK Central Upload & Download`
(UID `sssok-central-media`)가 추가된다. 기존 환경별 화면은 변경하지 않는다.

- 기존 미디어 화면의 52개 조회식 모두에 environment 필터를 추가했다.
- 개발·운영 선택과 집계 구간(5m/15m/1h)을 제공하고, 앱·로그 링크는 `/central/` 경로를 사용한다.
- 업로드, 미디어 처리, 회수 배치, 다운로드, 실행기 포화의 5개 행·21개 지표 패널이다.
  기본은 업로드 행만 펼치고 갱신은 60초로 둔다. 필요한 행을 펼쳐 본다.
- 업로드 항목은 고유 파일 수나 실제 R2 전송 성공률이 아니다. ZIP 준비 성공은 기기 저장 완료가 아니다.
- 최근 관측이 없는 지연·성공률 패널이나 아직 배포되지 않은 계측은 비어 있을 수 있다.
  정상 0으로 대체하지 않고 실제 수집 지표와 작업 발생 여부를 확인한다.
- 52개 PromQL 문법 검사는 통과했으며, 실제 환경별 값과 링크·행 펼치기는 Import 후 검증한다.

## 앱 화면 보강 (2026-10-13)

`backend-overview.json`은 기존 UID `sssok-central-backend`를 유지한 12개 패널 구성이다.
초기에는 수동 Import로 갱신했고, 자동 등록 적용 후에는 JSON 파일로 갱신한다. 서버 수집 설정이나 앱은 변경하지 않는다.

- API별 p95 상위 항목, 상태 코드별 4xx/5xx 응답량, Heap/Non-Heap 용량을 추가했다.
- GC 평균 정지 시간, JVM 스레드 상태, DB 연결 획득 평균 시간을 추가했다.
- HikariCP는 인스턴스·풀별 active/idle/pending/max를 구분한다.
- 일반 HTTP 패널에서 상태 검사·Actuator·장기 SSE를 제외한다. p95 상위 5개는
  시점별 순위라 전체 기간 범례는 5개보다 많을 수 있다.
- GC/DB 연결 기록이 없거나 시계열이 노출되지 않으면 빈 패널이 생길 수 있다.
  빈 값을 정상 0으로 대체하지 않고 실제 노출 지표와 발생 여부를 확인한다.
- 15개 PromQL 문법 검사를 통과했다. 새 JVM/Hikari 시계열의 실제 존재와 화면은
  개발·운영 각각 Import 후 확인해야 한다. 기본 갱신은 30초다.

## 호스트 화면 보강 (2026-10-13)

`host-overview.json`은 기존 UID `sssok-host-overview`를 유지한 10개 패널 구성이다.
같은 UID의 기존 화면을 갱신한다. 사용자 편집이 있다면 먼저 기존 화면 JSON을 내보내
보관한다. 자동 등록 구성과 적용 조건은 위 절을 따른다.

- 환경 선택으로 monitoring/dev/prod 또는 전체를 조회한다. 서버는 instance별로 구분한다.
- 가용 메모리 비율·용량, Swap 사용량·전체 용량·읽기·쓰기를 분리한다.
- CPU 비유휴 비율에는 iowait·steal이 포함된다. 별도 대기 비율과 CPU 수 대비 Load를 함께 본다.
- 루트 디스크 가용 공간 제외 비율과 실제 남은 용량을 함께 표시한다.
- 기존 cpu/meminfo/vmstat/loadavg/filesystem 수집기를 사용한다. 컨테이너 한도·OOM·CPU 크레딧은 포함하지 않는다.
- 13개 PromQL의 문법 검사를 통과했다. 실제 서버의 시계열 존재와 Grafana 렌더링은 Import 후 확인한다.

## 중앙 로그 대시보드 추가 (2026-10-13)

`logs.json`은 수동 Import용 `sssOK Central Logs` 대시보드다. 중앙 Grafana
(`/central/`)의 Dashboards → Import에서 업로드한다. 데이터소스 UID는 현재 중앙
설정의 `loki`를 사용하며, 기존 `sssok-logs`와 다른 UID라 기존 화면을 덮어쓰지 않는다.

- 환경은 dev/prod, 서비스는 sssok-backend/sssok-nginx, 수준은 All 또는 개별 수준을 선택한다.
- 최근 로그는 세 선택을 모두 적용하고, WARN/ERROR 패널은 환경·서비스만 적용한다.
- Request ID 패널은 환경과 응답 헤더 X-Request-ID만 적용해 앱·Nginx를 함께 찾는다.
  빈 ID는 결과가 없고, `/health` 요청은 앱 접근 로그를 생략하므로 Nginx만 나올 수 있다.
- 기본 최근 1시간, 자동 갱신 없음, 패널당 최대 100줄이다. 표시 건수는 전체 로그 건수가 아니다.
- JSON 정렬 표시와 상세 보기를 사용한다. 기존 Loki의 과거 로그는 중앙 조회에 포함되지 않는다.
- JSON 구조는 로컬 검사했으며 실제 중앙 Grafana Import·환경별 조회·Request ID 검색은
  서버 적용 후 확인해야 한다. 자동 등록 적용도 실제 로그 조회 검증을 대신하지 않는다.

> 2026-10-13 현재 상태는 [중앙 모니터링 작업 기록](../../../docs/deployment/CENTRAL_MONITORING_WORK_LOG_2026-10-13.md)을 참고한다.
> 개발·운영 호스트 수집과 ALB `/central/` 접속은 완료됐다. 아래 초기 기동 설명의
> 운영 미연결·ALB 미연결·SSM 조회 부분은 이전 단계 기록이며, 앱 지표·로그 이전과 중앙 전용 CD는 여전히 남아 있다.

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
`sssOK Host Overview` 대시보드에는 중앙·개발 호스트의 수집 상태, CPU 사용률,
가용 메모리, 루트 디스크 사용률, Swap 입출력을 표시한다. `dashboards.yml`과
`host-overview.json`을 함께 배치한 뒤 Grafana 컨테이너를 재생성하면 파일에서 등록된다.
운영 호스트는 수집 연결 후 별도 검증을 거쳐 추가한다.

Grafana는 127.0.0.1:3000으로만 공개하므로 임시 조회는 로컬 AWS CLI의 Session Manager
포트 포워딩(`AWS-StartPortForwardingSession`, 원격 3000)을 이용한다. 로컬 PC에 AWS CLI,
Session Manager 플러그인, 유효한 AWS 자격 증명이 필요하다. 포트 포워딩 세션 동안만
`http://127.0.0.1:<로컬 포트>/d/sssok-host-overview`로 접속한다. 이 접속은
`monitor.ssssok.com` 전환과 무관하다. 관리자 비밀번호는 중앙 서버의 `.env`에만 저장하고
콘솔이나 채팅에 출력하지 않는다.

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
중앙 서버는 현재 수동 배포 상태다. 전용 CI/CD 코드는 위 절차로 준비했으며 실제 실행은 남아 있다.

### 개발 재배포 안정화 완료 기준

- PR CI의 `개발 호스트 관측 설정 검증`은 기본 Compose 하나만으로 loopback 9100,
  호스트 마운트, CPU·메모리·Swap 수집 옵션을 유지하는지 검사하고 Nginx 문법도 검사한다.
- 개발 CD는 앱 교체 전에 `/etc/nginx/conf.d/sssok-dev.conf`가 레포의
  `dev.nginx.conf`와 같은지 검사한다. 주석·공백 차이도 실패하므로 최초 한 번은 검토한
  레포 파일을 그대로 설치해야 한다. 서버에서 수동 작성한 파일이 동작하더라도 이 절차가 필요하다.
- 검토한 소스가 있는 개발 서버 체크아웃에서 기존 파일을 별도 경로에 백업하고
  `sudo install -m 644 backend/monitoring/central/dev.nginx.conf /etc/nginx/conf.d/sssok-dev.conf`로
  설치한다. `sudo nginx -t` 성공 후에만 `sudo systemctl reload nginx`를 실행한다.
  검사 실패 시 백업 파일을 복원하며 reload하지 않는다. 기존 중복 설정 비활성화 절차는 위와 같다.
- PR을 develop에 병합하고 개발 CD가 성공한 뒤, 중앙 서버에서 최신 `check-dev.sh`를
  배치하고 `bash check-dev.sh`를 실행한다. 이는 중앙 Prometheus의 실제 수집과 필수 지표를 검사한다.
- 후속 개발 배포에서도 같은 검증을 통과하고 Grafana의 dev 지표가 유지되는지 확인한다.
  그때 임시 `dev-node.override.yml`을 제거할 수 있다. 병합·배포 이전 수동 복구는 완료로 보지 않는다.

GitHub CI 성공은 서버 적용 성공을 의미하지 않는다. 중앙 서버에 전용 배포 경로가 마련되기 전까지
중앙 검증은 수동 실행이다. 서버 시크릿·이미지 태그 파일은 Git으로 복사하지 않는다.

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

### Discord 연결 준비 — 아직 발송·배포하지 않음

`prepare-discord.py`는 현재 설정의 그룹화·중복 억제 규칙을 유지하면서 **검토용 후보**를 생성한다.
CD는 서버의 `discord-selection.json`을 읽어 후보를 생성한다. 선택 파일이 없거나 `{"dev": []}`이면 무발송이다. 기본 Compose와 저장소의 `alertmanager.yml`은 계속 무발송 상태이며, 코드 병합만으로 발송이 켜지지 않는다.

| 선택 | 포함하는 개발 알림 | 확인할 화면 |
| --- | --- | --- |
| `app` | 앱 중단, 5xx 비율, P95 응답 지연 | 중앙 앱 대시보드 |
| `host` | CPU 과부하, 메모리 부족, 디스크 부족·위험·24시간 내 고갈 예상 | 중앙 호스트 대시보드 |
| `logs` | Alloy 중단, 전송 실패, 로그 유실 | 중앙 로그 전송 대시보드 |

저장소의 `backend/monitoring/central`에서 후보만 확인한다. 서버에서 적용하는 명령이 아니다.

```bash
# 옵션이 없으면 기존 무발송 설정을 그대로 출력한다.
python3 prepare-discord.py
# 앱 알림만 개발 환경에서 이전하는 후보를 출력한다.
python3 prepare-discord.py --enable-dev app
# 여러 묶음은 옵션을 반복한다.
python3 prepare-discord.py --enable-dev app --enable-dev logs
```

- **라우팅**은 누가 받을지를 결정한다. `environment=dev`, `server=dev`, 선택한 알림 이름을 모두 만족해야 `discord-dev`로 간다. 나머지와 운영·중앙 알림은 기존 무발송 수신자로 간다.
- **메시지 템플릿**은 무엇을 보여줄지를 결정한다. 발생/복구 상태, 환경·서버·심각도, 로그 전송 목적지(`host`), 유실 사유와 중앙 Grafana 링크를 넣는다. 최대 3개 알림의 요약·설명을 길이 제한하여 표시한다. 누락한 상세는 대시보드에서 확인한다.
- **복구 알림**은 `send_resolved: true`로 설정한다. 실제 Discord 수신·전송 성공을 확인한 것은 아니며, 이번 검증은 라우팅과 메시지 렌더링까지다.
- **비밀값**은 설정에 URL을 적지 않고 `/run/secrets/central_discord_webhook` 파일 경로로 참조한다. 생성기는 비밀값을 읽거나 생성하지 않는다. 선택한 묶음이 있을 때만 CD가 읽기 전용 마운트를 생성한다. 서버 `.env`의 `CENTRAL_DISCORD_WEBHOOK_URL`을 CD가 읽어 배포별 비밀 파일을 자동 생성하고 실제 이미지 사용자 소유·권한 400으로 설정한다. URL을 Git·명령 인수·CI 로그에 넣지 않는다.

다음 단계는 **서버에서 개발 앱 알림을 실제 전환**하는 것이다. 배포가 관리하는 `alertmanager.yml`을 수동 덮어쓰지 않는다. 중앙 규칙의 무발송 상태를 먼저 점검한 뒤, 개발 앱 알림의 기존 발송을 끄고 서버 선택 파일에 `{"dev": ["app"]}`을 지정하여 중앙 CD를 실행한다. 별도 Alertmanager 사이에는 중복 억제가 공유되지 않으므로 이전 묶음별 발송 담당을 하나로 유지하고 전환 시각·짧은 공백을 기록한다. 되돌릴 때는 중앙 발송을 먼저 끄고 기존 발송을 켠다.

기존 중앙 검증 안의 `test-discord.py`가 실제 배포 이미지의 `amtool`로 설정 문법, 묶음별 환경 격리, 발생/복구 메시지를 검사한다. Docker 네트워크를 차단하며 실제 웹훅이나 Discord는 호출하지 않는다. 별도 CI job은 추가하지 않는다.


### CD에서 발송 선택·비밀 파일을 보존하는 방식

실행 대상은 **중앙 서버 `10.0.20.46`의 전용 CD 러너**다. 앱 서버는 변경하지 않는다.
목적은 설정 재배포 때 Discord 발송 범위가 초기화되지 않게 하고, 배포 실패 시 이전 실행 설정으로 복구하는 것이다.

| 파일 (`/home/ubuntu/monitoring` 기준) | 관리 주체 | 재배포·실패 복원 동작 |
| --- | --- | --- |
| `discord-selection.json` | 서버 담당자 | 덮어쓰거나 백업하지 않음. 예: `{"dev": ["app"]}` |
| `.env`의 `CENTRAL_DISCORD_WEBHOOK_URL` | 서버 담당자 | 최초 등록·회전 시 수정. CD가 덮어쓰거나 백업·출력하지 않음 |
| `secrets/central_discord_webhook-*` | 중앙 CD | 배포별 자동 생성, 실제 이미지 사용자 소유·권한 400. 이전 복구 경로를 위해 보존 |
| `alertmanager.yml` | CD | 매 배포마다 선택을 반영해 생성. 실패 시 이전 파일 복원 |
| `compose.notifications.yml` | CD | 활성화 때만 비밀 파일의 읽기 전용 마운트 포함. 실패 시 이전 파일 복원, 최초 배포 실패라면 삭제 |

CD는 다음 순서로 처리한다.

1. 배포 잠금을 획득하고 서버 선택 파일을 읽는다. 파일이 없으면 발송하지 않는다. 형식 오류·지원하지 않는 묶음·운영 환경 지정은 배포 실패로 처리한다.
2. 임시 폴더에 후보를 생성한다. 활성화 시 서버 `.env`를 Compose의 dotenv 문법으로 읽어 URL 형식을 검사한다. 셸의 동명 변수는 사용하지 않는다. 고유 이름의 비밀 파일을 생성하고 네트워크가 차단된 Docker 보조 컨테이너로 소유권·권한을 설정한다. URL은 `https://discord.com/api/webhooks/...` 형식만 허용한다.
3. 실제 배포 이미지의 기본 사용자로 비밀 파일을 읽을 수 있는지와 Alertmanager 문법을 검사한다. 네트워크를 차단하므로 이 검사는 Discord를 호출하지 않는다. 실패하면 Grafana 중단·기존 설정 복사 전에 종료한다.
4. 기존 설정과 Grafana 데이터를 백업한 뒤 후보를 적용하고 기존 배포 점검을 실행한다.
5. 실패하면 **이전의 실제 발송 설정과 마운트**를 복원한다. 서버 선택 파일은 담당자가 지정한 다음 배포 의도로 남으므로, 실패 직후에는 선택 파일과 실행 상태가 다를 수 있다. 재시도 전에 둘을 확인한다.

성공 기준은 같은 선택으로 재배포해도 발송 범위가 유지되고, 실패 후에는 이전 실행 설정이 돌아오며, 비밀값이 배포 산출물·백업·로그에 들어가지 않는 것이다. 이를 로컬 실패 주입 검사와 격리 컨테이너 검사로 확인한다. 실제 서버의 파일 소유권과 Discord 수신은 전환 단계에서 별도로 확인해야 한다.

주의: 알림 연결 후 수동 Compose 조작이 필요하면 `compose.yml`, `compose.web.yml`, **`compose.notifications.yml` 세 파일**을 함께 지정한다. 세 번째 파일을 생략하면 비밀 파일 마운트가 빠질 수 있다. 이 기능 이전 버전의 CD로 배포하는 것도 지원하지 않는다. 발송을 끄려면 선택을 `{"dev": []}`로 변경하고 이 기능을 포함한 CD로 배포한다. 배포 중에는 선택 파일·비밀 파일을 변경하거나 웹훅을 회전하지 않는다.

현재 변경은 로컬 구현·검증 단계다. 실제 서버 적용과 Discord 발송 수신은 별도 확인이 필요하다.


### 자원·응답 지연 초기 기준

CPU 85%·10분, 가용 메모리 10%·5분, P95 1초·5분은 초기 경고 기준이다. 부하 시험과 정상 트래픽 관측 후 조정한다. CPU는 idle을 제외한 전체 코어 평균이므로 I/O 대기 등도 포함하며 원인은 호스트 대시보드에서 확인한다. 기존 CentralMemoryLow는 HostMemoryLow로 통합하여 중앙 메모리 알림을 중복 생성하지 않는다.

응답 지연은 환경·서버별 HTTP 히스토그램으로 추정하며 Actuator·health·SSE를 지연과 최소 요청 수 양쪽에서 제외한다. 5분 요청 100건 미만이면 발생하지 않으므로 개발의 저트래픽 지연과 호출량이 적은 특정 API의 지연은 놓칠 수 있다. 시계열 부재를 정상 0으로 치환하지 않는다. 새 규칙은 조건 해소 시 발생 상태를 추가 유지하지 않으며, 기존 발송 그룹 대기·변경 주기는 그대로 적용한다.

CPU·메모리는 Discord host 묶음, 응답 지연은 app 묶음에 포함한다. 이미 해당 묶음을 활성화한 서버라면 다음 배포부터 새 규칙도 발송 대상이다. CPU와 응답 지연은 서로 억제하지 않는다. 실제 서버 적용·발송은 아직 수행하지 않았다.


### 리뷰 반영: 공통 수집 경로 장애와 배포 중 로그 폐기

Nginx 19100 포트를 공유하는 node/backend/alloy가 모두 실패하면 MetricsEndpointDown으로 알린다. node-exporter만 실패한 경우는 NodeMetricsDown으로 구분하며 앱·Alloy 알림을 숨기지 않는다. 대표 알림은 app/host/logs 어느 묶음을 선택해도 발송 대상으로 포함하여, 발송되지 않는 대표 알림이 선택된 개별 알림을 숨기는 상황을 방지한다. 대표 알림은 개별 수집 실패와 같은 5분 복구 유지 시간을 두어 공통 장애 복구 직후 잔류 개별 알림이 튀어나오는 것을 줄인다. 이후 개별 장애가 남아 있으면 다시 개별 알림으로 전달한다. 지표 세 가지 실패로 실제 서버 중단 원인을 확정하지 않으며, 수집 대상 자체가 삭제된 경우의 감지는 별도다.

중앙 CD는 백업 당시와 Loki/Nginx 설정 파일 내용이 같으면 강제 재생성을 생략한다. 서비스 정의 변경은 Compose의 변경 감지에 맡기며, bind 파일 내용이 바뀌면 강제 재생성한다. 실패 복원은 중간에 읽은 설정이 불확실하므로 기존처럼 강제 재생성한다. 이 조치는 변경 없는 배포의 로그 경로 중단을 줄이며 무중단이나 무유실을 보장하지 않는다.

LogEntriesDropped의 10분 증가량 > 0은 유지한다. 실제 폐기는 배포 중이라도 확인해야 하므로 자동 silence나 최소 폐기 건수 상향을 도입하지 않는다. 짧은 for는 단발 폐기 기록도 10분 창 안에 남아 있어 단순 지연에 그칠 수 있다. Loki/Nginx 설정 변경 또는 실패 복원 시에는 계획된 중단 가능성을 배포 전에 공유하고, 배포 후 중앙 목적지의 폐기 증가량·전송 재개·최신 로그를 확인한다. 과거 로컬 Loki 사본이 남아 있는지와 중앙 유실은 별도로 판단한다. 로그 단일 전송 전환 전에 실제 배포에서 폐기 여부를 측정하고 필요하면 버퍼·재시도 및 유지보수 알림 정책을 추가 설계한다.

### 로그 전송 대시보드 목적지 필터 검증

`log-transfer.json`의 목적지 선택은 `` host=~`${destination:regex}` `` 형태의
PromQL raw 문자열(백틱)을 사용한다. Grafana의 `:regex`가 IP의 점을 `\.`으로
이스케이프하므로, 이를 큰따옴표 문자열에 그대로 넣으면 PromQL 파싱 오류가 난다.
raw 문자열은 이스케이프를 정규식에 그대로 전달하여 IP를 정확하게 선택한다.
`All`의 사용자 지정 정규식 `.*`도 그대로 동작한다.

`python3 -B test-log-transfer.py`는 실제 대시보드에서 선택값과 8개 조회식을 읽어
환경 3개 × 목적지 3개의 72개 조회식을 배포 이미지의 promtool로 검사한다.
누적 전송 패널은 합성 지표로 9개 조합을 평가하여 환경·목적지 격리와 IP의 점이
와일드카드로 바뀌지 않는지도 확인한다. `validate.py`에서 함께 실행하므로 새 CI job은 없다.
Grafana 단일 값 regex 보간을 재현하는 검사이며 브라우저 E2E는 아니다.
배포 후에는 개발·운영 각각에서 All, 중앙 IP, 기존 Loki를 선택하여
8개 패널의 파싱 오류가 사라지고 선택한 목적지만 표시되는지 확인한다.


### 서버 .env로 Discord 웹훅 관리

중앙 서버 `/home/ubuntu/monitoring/.env`에 `CENTRAL_DISCORD_WEBHOOK_URL`을 등록한다.
원본 `.env`는 서버 담당자만 관리하고 저장소·CI 산출물·설정 백업에 포함하지 않는다.
URL을 명령 인수나 로그에 출력하지 않는다. 기존 Grafana 변수 등은 유지한다.

URL 등록만으로는 발송하지 않는다. `discord-selection.json`이 없거나 개발 선택이 비어
있으면 CD는 웹훅을 읽거나 비밀 파일을 생성하지 않는다. 개발 발송을 선택한 경우에만
자동 생성·권한 설정·실제 Alertmanager 사용자 읽기·설정 문법 검사를 수행한다.
잘못되거나 누락된 URL은 기존 서비스 중단 전에 배포를 실패시킨다.

매 배포마다 새 파일을 생성하므로 URL 회전 중 실패해도 이전 Compose override를 복원하면
이전 파일을 다시 사용한다. 파일 내용은 백업하지 않고 경로만 설정 백업에 남긴다.
이전 파일은 자동 삭제하지 않는다. 현재 실행 컨테이너 및 보존 중인 백업이 참조하는 파일은
삭제하면 복구가 불가능하다. 실패한 사전 검사에서 생성된 파일도 남을 수 있으므로,
보존 백업을 정리할 때 참조되지 않는 파일을 함께 정리한다. `.env`에 등록한 새 URL은
복구 시 되돌리지 않으며 다음 배포의 의도로 유지한다. 폐기된 웹훅 자체를 되살리지는 못한다.

이 기능을 적용한 CD부터 수동 비밀 파일 생성은 필요 없다. 기존 파일 기반 배포가 활성화된
서버는 `.env`에 URL을 먼저 등록해야 한다. 기존 고정 경로 파일도 이전 백업이 참조하는
동안 보존한다. 발송 묶음 전환과 Discord 실제 수신 확인은 별도 단계다.
