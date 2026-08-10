# 배포 가이드

## 전체 구조

```
main  ──PR──▶  deploy  ──push 트리거──▶  GitHub Actions
                                            │
                              ┌─────────────┴─────────────┐
                              │ 1. Docker 이미지 빌드      │
                              │ 2. GHCR 에 push           │
                              └─────────────┬─────────────┘
                                            │ SSH
                                            ▼
                                    EC2 (Docker + compose)
                                    ├─ sssok-app       (GHCR 에서 pull)
                                    └─ sssok-postgres  (볼륨 영속)
```

- 이미지 태그는 커밋 SHA를 사용한다. 롤백은 직전 태그로 되돌리는 것으로 끝난다.
- 배포 후 `/health` 를 최대 150초간 폴링하고, 실패하면 자동으로 직전 이미지로 롤백한다.

## 서버 디렉터리 구조

배포 경로(`DEPLOY_PATH`, 예: `/home/ubuntu/app`) 안에 아래 파일이 놓인다.

| 파일 | 만드는 주체 | 설명 |
| --- | --- | --- |
| `.env` | **사람이 1회 수동 생성** | DB 계정, JWT, R2 자격증명 |
| `image.env` | CI가 배포마다 덮어씀 | `BACKEND_IMAGE=ghcr.io/...:<sha>` |
| `image.env.prev` | CI가 자동 생성 | 롤백용 직전 태그 |
| `docker-compose.prod.yml` | CI가 배포마다 전송 | 컨테이너 정의 |

## 최초 세팅 (1회만)

### 1. EC2 준비

보안 그룹 인바운드:

| 포트 | 소스 | 용도 |
| --- | --- | --- |
| 22 | 내 IP | SSH |
| 8080 | 0.0.0.0/0 | 백엔드 (Nginx 붙이기 전 임시) |

### 2. Docker 설치

```bash
sudo apt-get update
sudo apt-get install -y ca-certificates curl
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
sudo chmod a+r /etc/apt/keyrings/docker.asc
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo $VERSION_CODENAME) stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
sudo usermod -aG docker $USER
```

`usermod` 이후 **SSH 재접속**해야 sudo 없이 docker를 쓸 수 있다. CI 스크립트가 sudo 없이 실행되므로 이 단계는 필수다.

### 3. 배포 디렉터리와 `.env` 생성

```bash
mkdir -p ~/app && cd ~/app
```

`.env` 를 만들고 아래 값을 채운다 (`backend/.env.prod.example` 참고).

```bash
cat > .env <<'EOF'
DB_NAME=sssok
DB_USERNAME=sssok
DB_PASSWORD=여기에_강한_비밀번호
JWT_SECRET=여기에_openssl_rand_base64_32_결과
R2_ENDPOINT=https://<account-id>.r2.cloudflarestorage.com
R2_ACCESS_KEY=
R2_SECRET_KEY=
R2_BUCKET=sssok-prod
R2_PUBLIC_BASE_URL=
EOF
chmod 600 .env
```

### 4. 배포용 SSH 키 발급

로컬에서 실행한다. **기존에 노출된 키는 쓰지 않는다.**

```bash
ssh-keygen -t ed25519 -f ~/.ssh/sssok_deploy -N "" -C "github-actions-deploy"
```

공개키를 서버에 등록한다.

```bash
ssh-copy-id -i ~/.ssh/sssok_deploy.pub ubuntu@<EC2_IP>
```

개인키(`~/.ssh/sssok_deploy`) **전문**을 GitHub Secret `DEPLOY_SSH_KEY` 에 넣는다.

### 5. GitHub Secrets 등록

`Settings → Environments → production → Environment secrets`

| 이름 | 예시 |
| --- | --- |
| `DEPLOY_HOST` | `13.125.x.x` |
| `DEPLOY_USER` | `ubuntu` |
| `DEPLOY_SSH_KEY` | `-----BEGIN OPENSSH PRIVATE KEY-----` 로 시작하는 전문 |
| `DEPLOY_PORT` | `22` |
| `DEPLOY_PATH` | `/home/ubuntu/app` |

DB·JWT·R2 값은 서버 `.env` 에 있으므로 GitHub Secret으로 넣지 않는다.

### 6. GHCR 패키지 접근 권한

첫 배포 후 패키지가 생성되면
`https://github.com/orgs/woowacourse-teams/packages` 에서 `2026-sssok/backend` 를 열고
**Package settings → Manage Actions access** 에서 이 저장소에 `Write` 권한이 있는지 확인한다.

## 배포하기

```bash
git checkout deploy
git merge main
git push origin deploy
```

`backend/**` 변경이 있으면 워크플로가 자동 실행된다. 변경이 없을 때는 Actions 탭에서 `Backend Deploy` → `Run workflow` 로 수동 실행한다.

## 운영 명령어

배포 디렉터리에서 실행한다.

```bash
cd ~/app
export COMPOSE="docker compose --env-file .env --env-file image.env -f docker-compose.prod.yml"
```

| 목적 | 명령 |
| --- | --- |
| 상태 확인 | `$COMPOSE ps` |
| 앱 로그 | `$COMPOSE logs -f app` |
| 재시작 | `$COMPOSE restart app` |
| 전체 내리기 | `$COMPOSE down` |
| 헬스체크 | `curl -i localhost:8080/health` |

### 수동 롤백

```bash
cd ~/app
cat image.env.prev > image.env
docker compose --env-file .env --env-file image.env -f docker-compose.prod.yml up -d
```

특정 커밋으로 되돌리려면 `image.env` 의 태그를 직접 바꾼다.

```bash
echo "BACKEND_IMAGE=ghcr.io/woowacourse-teams/2026-sssok/backend:<커밋SHA>" > image.env
```

## DB 마이그레이션

운영은 `ddl-auto: validate` + Flyway 조합이다. **엔티티를 추가·변경하면 마이그레이션 SQL을 반드시 함께 작성해야 한다.** 없으면 `validate` 가 실패해 앱이 뜨지 않는다.

```
backend/src/main/resources/db/migration/
├── V1__create_room.sql
├── V2__create_file.sql
└── V3__add_room_expired_at.sql
```

- 파일명: `V{번호}__{설명}.sql`
- 이미 배포된 마이그레이션 파일은 **절대 수정하지 않는다** (체크섬 불일치로 기동 실패). 수정이 필요하면 새 버전을 추가한다.

## 트러블슈팅

| 증상 | 원인과 조치 |
| --- | --- |
| `permission denied ... docker.sock` | 서버에서 `usermod -aG docker` 후 재접속하지 않음 |
| `denied: installation not allowed to Create organization package` | 워크플로의 `permissions: packages: write` 누락 또는 org 패키지 정책 |
| 헬스체크 실패 후 롤백됨 | `$COMPOSE logs app` 확인. 대부분 `.env` 값 누락 또는 Flyway 마이그레이션 오류 |
| `Schema-validation: missing table` | 엔티티에 대응하는 마이그레이션 SQL 미작성 |
| compose 가 `BACKEND_IMAGE` 를 못 찾음 | `image.env` 부재. 최초 배포 전이거나 경로가 틀림 |
