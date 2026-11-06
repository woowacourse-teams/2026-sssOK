# 갤러리 MCP 수동 배포

2026-11-02 · #644. EC2 기본 설정·runner 등록·모델 배치는 사용자 완료 확인 기준이다.
이 파이프라인은 로컬 구현 단계이며 실제 GHCR 게시·EC2 배포는 아직 실행하지 않았다.
기존 오프라인 전달 대신 GitHub Actions에서 빌드하고 전용 runner가 배포한다.

AWS 자원은 조직의 최신 인프라 정책을 확인한 뒤 구성한다. 비용은 기존 팀 자원과 합산하며,
필수 태그·허용 사양·IAM 역할·서브넷·보안 그룹은 조직 정책에 맞춰 확인한다.
인스턴스는 `sssok-mcp`의 t4g.small(ARM64)로 시작하며, 분석 컨테이너는
2 CPU·1400MiB·추가 스왑 없음·단일 워커를 유지한다. 증설은 실제 측정 후 결정한다.

## 최초 준비

- runner: `sssok-mcp`, 라벨 `[self-hosted, linux, ARM64, mcp]`, 서비스 사용자는 Ubuntu의 `ubuntu`, AL2023의 `ec2-user`.
- GitHub Environment `mcp`의 배포 허용 브랜치와 승인 규칙을 팀 기준으로 설정한다.
  공개 저장소 PR 코드는 GitHub hosted runner에서만 검사하며 EC2 runner에는 보내지 않는다.
- GHCR 패키지에 이 저장소의 Actions 읽기·쓰기 권한이 있어야 한다. 로그인에는 작업별
  `GITHUB_TOKEN`을 사용한다. 빌드 job만 packages:write, 배포 job은 packages:read다.
- 배포 루트는 서비스 사용자의 `$HOME/sssok-mcp`이며 해당 사용자가 쓸 수 있어야 한다.
  다른 경로를 사용하면 `MCP_DEPLOY_ROOT`로 지정한다. 이전할 때 `.env`의 절대 경로도 새 루트로 맞춘다.
- 모델·토큰은 이미 준비한 `gallery/models`, `gallery/secrets`에 유지한다.
- `gallery/.env`를 `sudo nano`로 작성했다면 다음을 실행한다. 토큰 내용은 출력하지 않는다.

```bash
# runner 서비스 사용자로 실행
deploy_root=${MCP_DEPLOY_ROOT:-$HOME/sssok-mcp}
sudo chown "$(id -u):$(id -g)" "$deploy_root/gallery/.env"
sudo chmod 600 "$deploy_root/gallery/.env"
test -r "$deploy_root/gallery/.env" && echo 'env readable'
test -w "$deploy_root" && echo 'deploy root writable'
```

`.env` 항목은 [.env.example](.env.example)을 참고한다. 모두 `GALLERY_` 접두사를 사용하며,
`GALLERY_IMAGE`는 파이프라인이 별도 관리한다.
개발 사진부터 standalone으로 검증한다. 호스트 허용 목록은 버킷 구분이나 사용자 인가를 대신하지 않는다.
컨테이너 UID/GID 10001의 모델·토큰 읽기 및 data 쓰기 권한도 필요하며 배포 사전 검사에서 확인한다.

## 실행

1. 변경을 팀 절차에 따라 커밋·PR·리뷰한다. `workflow_dispatch` 파일이 저장소 기본 브랜치에
   존재해야 수동 실행이 가능하다. 기본 브랜치가 main이면 develop 병합만으로 버튼이 생기지 않는다.
   이 요건 때문에 보호 브랜치에 직접 push하거나 승격 절차를 우회하지 않는다.
2. Actions → Gallery MCP Deploy → Run workflow에서 검토된 브랜치를 선택한다.
3. ARM64 CI → 이미지 빌드·GHCR 게시 → 전용 EC2 배포 순서로 진행된다.
4. 배포는 이미지 digest로 고정하며 호스트에서 이미지를 빌드하지 않는다.
5. 모델 4개 체크섬 → 신규 요청 차단·기존 작업 종료 대기(최대 600초) → 교체 →
   health·ready·미인증 거절·MCP initialize/tools-list 순서로 검사한다.
6. 성공한 이미지·설정은 `releases/gallery-*`와 `current` 링크에 남는다. 링크 갱신 후 요청 접수를 재개한다.

CI는 실제 모델 가중치·사진을 내려받지 않는다. 모델 추론과 R2 분석은 EC2에서 별도 검증한다.
테스트 수신기 재전송·EC2 재부팅·자원 사용량·실제 롤백도 서버 구축 완료 전에 확인한다.

## EC2 검증 완료 기준

- 실제 R2 서명 URL로 분석하고 기존 62장의 5개 그룹·74개 연결과 비교한다.
- 처리 시간·최대 메모리·OOM·호스트 여유 메모리·CPU 크레딧·디스크를 확인한다.
- 연속 요청·대기열 포화·시간 초과와 분석 중 재시작 후 실패 보고·새 회차 재시도를 확인한다.
- 수신기 장애 후 동일 결과 재전송·수신 확인, 미전송 영수증 보존과 만료·임시 파일 정리를 확인한다.
- EC2 재부팅 후 Docker·MCP 자동 기동, 업데이트 실패 후 실제 롤백을 확인한다.
- 서비스 중단·자원 부족·결과 전달 적체를 관측하고 대응 절차를 확정한다.

단일 분석 성공만으로 운영 준비 완료로 판단하지 않는다. Docker의 unhealthy 상태만으로
컨테이너가 자동 재시작되는 것도 아니다. 내부 TLS·호출 출발지 제한과 모니터링 연결은 후속 검증 대상이다.

## 실패 및 복구

- 일반 업데이트에서는 이전·새 릴리스의 Compose `/data` 바인드 경로가 같아야 한다.
  경로가 다르면 이미지 다운로드·기존 요청 차단·컨테이너 교체 전에 배포를 거절한다.
  미전송 결과가 없더라도 이 제한을 적용한다. 데이터 이전이 필요하면 별도 점검 시간에
  신규 요청과 결과 기록을 중지하고, 영수증을 포함한 데이터를 백업·이전한 뒤 권한·조회·재전송을
  검증해야 한다. `.env` 경로만 변경하거나 기존 데이터 경로의 심볼릭 링크 대상을 바꾸지 않는다.
- 이미지 다운로드·모델 검사 실패: 기존 컨테이너를 교체하지 않는다.
- 대기 작업이 시간 내 끝나지 않음: 교체를 취소하고 요청 접수를 다시 허용한다.
- 교체·연결 검사·접수 재개 실패: 직전 release의 이미지와 설정으로 복구하고 MCP 연결을 재검사한다.
  복구 검사가 성공하면 `current` 링크도 이전 버전으로 되돌린다. 단, 아래 JSON → SQLite 형식 전환은 예외다.
- 첫 배포 실패: 실패 컨테이너만 정지한다. 데이터·모델·토큰은 보존한다.
- 미전송 결과는 영속 data에 남는다. 모델·토큰은 공용 파일이므로 별도 변경 시 이 스크립트가
  이전 모델·토큰 내용까지 복원하지 않는다. 영수증 형식 변경은 하위 호환 검증이 필요하다.
- SIGKILL·호스트 장애 시 trap 실행은 보장되지 않는다. `current`의 이미지·컨테이너 상태를
  대조하고 복구 후 `/data/draining`을 제거해야 한다. 자동으로 잔여 marker를 지우지 않는다.
- 호스트 디스크 공간을 관측한다. 현재·직전 이미지와 release를 보존하고, 이 스크립트는
  다른 서비스에 영향을 주는 전역 image prune이나 데이터 삭제를 하지 않는다.

현재 포트는 `127.0.0.1:8818`에만 노출한다. Spring 원격 연결용 TLS·네트워크와
Spring/RDS 수신 API는 후속 작업이다.

## JSON 실행 기록에서 SQLite로 전환

- 새 릴리스는 `storage-version=sqlite-v1`을 기록하고 `/data/executions-v1.sqlite3`를 사용한다.
  drain은 SQLite의 QUEUED/RUNNING을 읽는다. 이전 완료 전에는 기존 JSON도 확인하고,
  이전 완료 후에는 오래된 JSON 백업을 작업 상태로 읽지 않는다.
- 최초 전환 전에 기존 요청을 중단하고 부모·워커가 모두 종료된 상태에서 `/data` 전체를 백업한다.
  새 서버의 시작 복구가 기존 JSON을 한 번만 이전한다. 미전송 결과·대기 작업·수신 확인 상태를
  유지하며 원본 JSON은 남긴다. 손상된 기록이나 읽기 오류는 무시하지 않고 시작을 실패시킨다.
- SQLite 파일이 생성된 뒤 새 릴리스 검사에 실패하면 JSON 전용 이미지로 자동 롤백하지 않는다.
  새 컨테이너를 멈추고 접수 차단을 유지하며 수동 복구를 요구한다. JSON 전용 구버전은
  새 SQLite 결과를 읽지 못하므로 이를 정상 롤백으로 취급하면 미전송 결과가 보이지 않게 된다.
- 이 경우 DB와 JSON 백업을 삭제하지 않는다. SQLite를 지원하는 이미지·설정으로 수동 복구하고,
  시작·조회·수신 확인·smoke 검증 후 해당 릴리스로 `current`를 정정하고 접수를 재개한다.
  이전 JSON 스냅샷으로 되돌려야 한다면 전환 이후 생성된 실행·결과를 먼저 확인하고 보존해야 한다.
- SQLite끼리의 호환 릴리스는 기존 자동 롤백 절차를 사용한다. 실행 중 백업은 SQLite backup API를
  사용하거나 모든 프로세스를 중지하고 전체 data를 복사한다. DB 파일만 임의로 복사하거나 journal을
  삭제하지 않는다. JSON 백업의 입력 URL 보존 기간·접근 권한은 운영자가 관리한다.

## 공식 자료

- [수동 실행 및 기본 브랜치 요건](https://docs.github.com/en/actions/how-tos/manage-workflow-runs/manually-run-a-workflow)
- [GitHub hosted ARM64 runner](https://docs.github.com/en/actions/how-tos/write-workflows/choose-where-workflows-run/choose-the-runner-for-a-job)
- [Compose up과 wait](https://docs.docker.com/reference/cli/docker/compose/up/)

### SQLite schema upgrades (#661, #710)

Current releases record `storage-version=sqlite-v3`. Drain and stop the old release and back up
the database before upgrading. The DB filename remains `executions-v1.sqlite3`; its user_version
becomes 3. Migration preserves existing jobs and assigns them to DEV. Schema v2 introduced
job kinds; schema v3 adds the authenticated environment.

If replacement fails, stop the failed release before inspecting the DB version read-only.
A sqlite-v1 release accepts only v1, sqlite-v2 accepts v1/v2, and sqlite-v3 accepts v1/v2/v3.
After migration to v3, automatic rollback to v1/v2 is blocked. Preserve data and restore a
v3-compatible release. Restoring a pre-migration backup requires separately accounting for
newly accepted jobs and undelivered results. JSON-only rollback requires no SQLite DB.
Unknown/unreadable DB versions, a missing DB for a SQLite release, or failure to stop the new
release block automatic rollback and resume. Prepare all six checked model files before
upgrading; see [similar-photo setup](../SIMILAR_PHOTOS.md).

## 한 MCP 서버에서 DEV·PROD 분리 (#710)

백엔드 두 환경은 동일한 MCP URL을 사용하되, 서로 다른 `GALLERY_TOKEN`으로 인증한다.
MCP는 인증 토큰으로 환경을 결정하며 요청 인자나 헤더의 환경 이름·콜백 URL을 신뢰하지 않는다.
작업에 저장한 환경으로 콜백 URL과 콜백 토큰을 선택한다. 환경 설정이 없으면 다른 환경으로
대신 보내지 않고 미확인 결과를 보존한다. worker 한 개와 전체 큐 한도는 그대로 공유한다.

| 설정 | DEV 백엔드 | PROD 백엔드 |
|---|---|---|
| `GALLERY_MCP_URL` | `https://api.ssssok.com/mcp` | 동일 |
| `GALLERY_TOKEN` | JSON의 `dev.token` | JSON의 `prod.token` |
| `GALLERY_BACKEND_TOKEN` | JSON의 `dev.backend_token` | JSON의 `prod.backend_token` |

MCP의 `gallery/.env`에는 환경 파일의 호스트 경로를 설정한다. Compose가 컨테이너 내부의
`GALLERY_ENVIRONMENTS_FILE=/run/secrets/gallery_environments`를 자동 지정한다.

```dotenv
GALLERY_ENVIRONMENTS_PATH=/home/ec2-user/sssok-mcp/gallery/secrets/environments.json
GALLERY_STANDALONE=false
```

[environments.example.json](environments.example.json)을 바탕으로 실제 파일을 만들고,
토큰 네 개를 서로 다른 32자 이상 값으로 채운다. 예시의 짧은 placeholder는 검증에 실패한다.
파일 내용은 Git에 올리지 않는다. 컨테이너 UID 10001이 읽을 수 있는 최소 권한을 부여하고,
배포 전에 `control.py verify`로 실제 컨테이너 접근·설정을 검사한다.
기존 `GALLERY_TOKEN_PATH`와 `GALLERY_BACKEND_TOKEN_PATH` 마운트는 전환 호환용으로 유지하지만,
환경 파일이 활성화되면 그 토큰과 단일 `GALLERY_BACKEND_URL`은 인증·콜백에 사용하지 않는다.
환경 파일을 지정하지 않은 기존 구성은 DEV 전용으로 동작한다.
환경별 download 호스트를 별도로 나누지는 않으며 기존 정확한 호스트 허용 목록을 유지한다.

배포 순서:

1. 기존 실행 이미지와 설정, SQLite DB를 백업하고 DEV 토큰을 `dev` 항목에 유지한다.
2. PROD 요청·콜백 토큰과 백엔드 수신 endpoint의 존재를 확인한다. 백엔드의 기능 활성화 값도 환경별로 확인한다.
3. 새 환경 파일을 준비하고 이전 앱을 drain·중지한 뒤 새 버전을 실행한다.
4. 배포 smoke는 DEV·PROD 토큰 각각으로 인증·도구 목록을 검사한다. 환경 간 조회·재접수 차단은 테스트에서 검증한다.
5. PROD 백엔드에 일치하는 두 토큰과 MCP URL을 반영하고, 실제 분석·콜백을 환경별로 확인한 뒤 정규 배포를 활성화한다.

SQLite는 v3으로 변경된다. 기존 v1/v2/JSON 작업은 **DEV**로 귀속하며 실행 중인 이전 서버가 있으면
변환을 거부한다. 같은 business job ID는 환경별로 사용할 수 있다. execution/request UUID는 서버 전체에서
유일하게 유지하며 다른 환경이 같은 UUID로 재요청하면 거절한다. 다른 환경의 결과 조회도 거절한다.
기존 환경 이름의 목적지를 다른 환경으로 재지정하지 않는다. 재시도는 저장된 환경의 현재 고정 설정을 사용한다.

**v3 변환 후 v1/v2 이미지로 자동 롤백하지 않는다.** 배포 스크립트가 이를 차단하며 앱을 중지 상태로
유지한다. v3 호환 버전으로 복구하거나, 신규 요청을 차단한 상태에서 검증한 변환 전 백업과 이전 설정을
함께 복원해야 한다. 새로운 PROD 작업이 생긴 뒤 이전 DEV DB로 단순 덮어쓰면 작업이 유실될 수 있다.

### 운영 백엔드보다 먼저 설정을 준비하는 경우

운영 서버 `.env`에 MCP 주소·요청 토큰·콜백 토큰·작업 활성화 값을 미리 기록할 수 있다.
실행 중인 컨테이너는 재생성 전까지 기존 환경을 유지한다. 현재 운영 이미지에 사진 정리 API가
없다면 환경변수 준비를 운영 연동 완료로 판단하지 않는다.

다음 릴리스 순서는 **환경 분리 MCP 배포·양쪽 토큰 인증 확인 → 콜백 API를 포함한 운영 백엔드
릴리스 → 실제 PROD 분석·콜백 검증**이다. 구버전 MCP가 실행 중일 때 새 PROD 토큰을 사용하는
백엔드를 먼저 배포하지 않는다. MCP의 호스트 `.env`와 `environments.json`을 준비했더라도
실행 중인 릴리스의 설정·이미지·DB는 그대로이므로 새 MCP 배포가 별도로 필요하다.
