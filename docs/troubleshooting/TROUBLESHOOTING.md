# 트러블슈팅 기록

배포 파이프라인처럼 여러 번 시행착오를 거친 문제는, 나중에 같은 증상을 다시 만났을 때
바로 찾아볼 수 있도록 여기에 기록한다. 형식은 **증상 → 원인 → 해결** 순서로 통일한다.

---

## 2026-08-11 — 배포 파이프라인: self-hosted 러너 전환 및 빌드 시간 단축

### 배경

`main → deploy` 반영 후 처음으로 실제 배포를 시도하면서, GitHub-hosted 러너 기반 SSH 배포가
근본적으로 동작하지 않는다는 것을 발견했다. 원인을 해결하는 과정에서 self-hosted 러너로
아키텍처를 바꿨고, 그 김에 배포 이미지 빌드 시간(9분 → 1분 31초)도 함께 줄였다.

### 한눈에 보는 타임라인

| # | 증상 | 원인 | 해결 |
|---|---|---|---|
| 1 | SCP 스텝에서 `dial tcp ... i/o timeout` | GitHub-hosted 러너는 매 실행마다 IP가 바뀌는데, 보안 그룹이 SSH(22)를 특정 IP만 허용 | self-hosted 러너를 EC2 내부에 설치, 배포 잡을 로컬 실행으로 전환 |
| 2 | `Cannot connect to the Docker daemon` | `docker.socket`이 비활성화되어 `dockerd`가 소켓을 못 받고 죽음 | `systemctl enable/start docker.socket` |
| 3 | 이미지 pull 시 `unauthorized` | 서버에서 GHCR 로그인이 안 되어 있음 (private 패키지) | PAT 발급 후 서버에서 `docker login ghcr.io` 1회 실행 |
| 4 | `no matching manifest for linux/arm64/v8` | 서버는 ARM64(Graviton)인데, GitHub-hosted 러너(amd64)가 amd64 전용 이미지만 빌드 | QEMU + buildx로 `linux/amd64,linux/arm64` 멀티 아키텍처 빌드 |
| 5 | self-hosted 배포 시 `cp: Permission denied` | `docker-compose.prod.yml`이 `root` 소유로 남아 있어 러너 실행 계정(`ubuntu`)이 덮어쓰지 못함 | `sudo chown -R ubuntu:ubuntu ~/app` |
| 6 | 이미지 빌드에 9분 소요 | Docker 멀티스테이지 빌드 안에서 arm64로 크로스 컴파일 — QEMU 에뮬레이션 위에서 Gradle/JVM 컴파일이 극도로 느림 | JAR을 러너에서 네이티브로 먼저 빌드, Docker는 완성된 JAR만 복사하도록 분리 |

---

### 1. SSH 연결 타임아웃 → self-hosted 러너 전환

**증상**

```
error copy file to dest: ***, error message: dial tcp ***:***: i/o timeout
```

`compose 파일 전송`(scp-action) 스텝이 30초 뒤 타임아웃으로 실패했다.

**진단 과정**

`dial tcp ... timeout`은 SSH 인증(publickey)이 실패한 게 아니라, **TCP 연결 자체가 성사되지
않았다**는 뜻이다. 키가 틀렸다면 `Permission denied (publickey)`처럼 연결 *이후* 단계에서
나는 에러가 났을 것이다. 이 구분 덕분에 SSH 키 문제가 아니라 네트워크(보안 그룹) 문제라는
걸 코드를 보지 않고도 특정할 수 있었다.

실제로 보안 그룹을 확인해보니, SSH(22)가 사무실/VPN 등 특정 IP 대역만 허용하도록 세밀하게
잠겨 있었다. 반면 GitHub-hosted 러너는 매 실행마다 Azure의 대규모 IP 풀에서 무작위로
배정되는 임시 VM이라, 실행할 때마다 IP가 달라진다. 그래서 이 화이트리스트 방식과는
구조적으로 맞지 않는다.

**검토한 대안**

| 대안 | 특징 |
|---|---|
| 보안 그룹에 `0.0.0.0/0` 허용 | 가장 간단하지만, 이 보안 그룹의 "특정 IP만 허용" 정책과 정면충돌 |
| IAM으로 배포 시점에만 러너 IP를 동적으로 허용/제거 | 정책은 지키지만 AWS IAM 사용자 발급 등 준비 작업이 많음 |
| AWS SSM Session Manager | 22번 포트 자체가 필요 없어져서 가장 근본적이지만, 배포 스텝을 SSM 명령 기반으로 다시 짜야 함 |
| **self-hosted 러너 (채택)** | EC2 안에서 잡이 직접 실행되므로 "밖에서 안으로 접속"하는 구조 자체가 사라짐. 기존 보안 그룹 정책과 철학이 같고, 재작업 범위도 상대적으로 작음 |

**해결**

1. GitHub UI(`Settings → Actions → Runners`)에서 EC2용 러너(Linux, ARM64) 등록
2. `svc.sh install` / `svc.sh start`로 systemd 서비스 등록 (재부팅 후에도 유지)
3. `deploy.yml`의 `서버 배포` 잡에서 `appleboy/scp-action`, `appleboy/ssh-action` 제거
4. `runs-on: [self-hosted, linux, ARM64]`로 변경, 파일 복사·이미지 교체·헬스체크를 전부 로컬 `run:` 스텝으로 전환

**결과**

`서버 배포` 잡이 (SSH 연결 시도 후 타임아웃으로 실패하던 것에서) **11초 만에 시작**되는 것으로
확인됐다 — 러너 자체가 배포 대상이라 연결 지연이 없어졌다.

> ⚠️ self-hosted 러너는 그 서버에서 임의 코드를 실행할 권한을 가진다. 이 워크플로는 `deploy`
> 브랜치 push(이미 리뷰된 코드)에만 반응하므로 지금은 위험이 낮지만, 외부 기여자의 fork PR을
> 받게 되면 `Settings → Actions → General`에서 fork PR의 워크플로 자동 실행을 반드시 막아야
> 한다.

---

### 2. Docker 데몬 기동 실패

**증상**

```
Cannot connect to the Docker daemon at unix:///var/run/docker.sock. Is the docker daemon running?
```

**진단**

`sudo systemctl status docker`로 확인하니 `failed (Result: exit-code)`, 재시작을 반복하다
포기한 상태였다. `journalctl -u docker`의 실제 에러는:

```
failed to load listeners: no sockets found via socket activation: make sure the service was started by systemd
```

`docker.service`는 `-H fd://` 옵션으로 시작하도록 설정되어 있는데, 이건 `dockerd`가 소켓을
직접 여는 게 아니라 systemd의 `docker.socket`이 미리 열어서 넘겨주는 방식(소켓 액티베이션)을
전제로 한다. `docker.socket`이 비활성화되어 있으니 `dockerd`가 소켓을 못 받고 죽은 것이었다.

**해결**

```bash
sudo systemctl enable docker.socket
sudo systemctl start docker.socket
sudo systemctl start docker
```

---

### 3. GHCR 인증 실패 (`unauthorized`)

**증상**

```
Error response from daemon: error from registry: unauthorized
```

**원인**

GHCR 패키지가 조직 소속 private 패키지인데, 서버에서 `docker login ghcr.io`를 한 적이
없었다.

**해결**

GitHub Personal Access Token(classic, `read:packages` 스코프)을 발급해서 서버에서 1회
로그인했다. 로그인 정보는 `~/.docker/config.json`에 캐시되므로, 이후 `docker compose pull`은
재로그인 없이 계속 재사용된다. (CI 자동 배포에서는 워크플로가 자동 발급하는
`secrets.GITHUB_TOKEN`을 쓰므로 이 수동 로그인 자체가 필요 없다 — 지금은 수동으로 먼저
검증하는 단계라 필요했을 뿐이다.)

> 이 과정에서 발급한 토큰이 실수로 채팅에 평문으로 노출된 적이 있어, 즉시 폐기 후 재발급했다.
> **토큰/키는 절대 채팅이나 커밋에 붙여넣지 않는다.**

---

### 4. 아키텍처 불일치 (`no matching manifest for linux/arm64/v8`)

**증상**

```
Error response from daemon: no matching manifest for linux/arm64/v8 in the manifest list entries: no match for platform in manifest: not found
```

**원인**

서버(EC2)는 ARM64(Graviton) 인스턴스인데, GitHub-hosted 러너는 amd64라 기본 설정으로는
amd64 전용 이미지만 빌드되어 있었다.

**해결**

`docker/setup-qemu-action`으로 크로스 아키텍처 빌드 환경을 만들고,
`docker/build-push-action`에 `platforms: linux/amd64,linux/arm64`를 지정해 두 아키텍처를
동시에 빌드하도록 했다.

---

### 5. self-hosted 배포 시 `Permission denied`

**증상**

```
cp: cannot create regular file '***/docker-compose.prod.yml': Permission denied
```

**진단**

디렉터리 소유자(`ubuntu`)와 러너 실행 계정(`ubuntu`)이 일치하는데도 실패했다. `ls -la`로
확인해보니, 문제는 디렉터리가 아니라 **그 안에 이미 있던 파일**이었다.

```
-rw-r--r-- 1 root   root   1845 Aug 11 00:54 docker-compose.prod.yml
```

`cp`는 대상 파일이 이미 존재하면 그 파일 자체의 쓰기 권한을 보는데, 예전에 다른 경로(수동
테스트 중 `sudo`를 쓴 적)로 생긴 파일이 `root` 소유로 남아 있었다.

**해결**

```bash
sudo chown -R ubuntu:ubuntu ~/app
```

---

### 6. 빌드 시간 단축 — 컴파일과 이미지 조립 분리

**증상**

아키텍처 문제(#4)를 고치고 나니 배포는 성공했지만, `이미지 빌드 및 푸시` 단계가 **9분** 가까이
걸렸다.

**원인**

기존 `Dockerfile`은 멀티스테이지 빌드 안에서 `./gradlew bootJar`로 컴파일까지 했다. amd64
러너에서 arm64 이미지를 만들려면 QEMU로 arm64를 에뮬레이션해야 하는데, **에뮬레이션된 CPU
위에서 JVM 컴파일 전체를 돌리는 건 극도로 느리다.** QEMU는 파일 복사처럼 가벼운 작업에는
비용이 거의 없지만, 무거운 연산(컴파일)에는 수 배의 페널티가 붙는다.

**해결**

컴파일과 이미지 조립을 분리했다.

1. GitHub Actions 러너에서 JDK 21 + Gradle로 **네이티브(amd64)** 컴파일 (`./gradlew bootJar -x test`)
2. `Dockerfile`에서 빌드 스테이지를 제거하고, 미리 빌드된 `build/libs/*.jar`을 그대로 복사만 하는 단일 스테이지로 축소
3. `.dockerignore`에서 `build/*`는 제외하되 `build/libs`만 예외로 허용 (`RUN` 컴파일이 없으니, QEMU 에뮬레이션 대상은 "유저 생성" 같은 가벼운 명령 하나뿐이라 비용이 거의 사라짐)

**결과**

| 단계 | 이전 | 이후 |
|---|---|---|
| 이미지 빌드 및 푸시 (전체) | 약 9분 | **1분 31초** |
| └ JAR 빌드 (네이티브) | — (Docker 안에서 컴파일) | 48초 |
| └ 이미지 조립 (Docker) | ~9분 (QEMU 위에서 컴파일) | **18초** |
| 서버 배포 (헬스체크 포함) | 30~35초 | 30초 |
| **전체 파이프라인** | 약 13분 | **약 2분 20초** |

---

### 최종 아키텍처

```
main  ──PR──▶  deploy  ──push 트리거──▶  GitHub Actions (GitHub-hosted)
                                            │
                              ┌─────────────┴─────────────┐
                              │ 1. JDK/Gradle로 JAR 네이티브 빌드 │
                              │ 2. QEMU+buildx로 멀티 아키텍처   │
                              │    이미지 조립 (JAR 복사만)      │
                              │ 3. GHCR 에 push                │
                              └─────────────┬─────────────┘
                                            │
                                            ▼
                              GitHub Actions (self-hosted, EC2 내부)
                                            │ 로컬 명령 (SSH 없음)
                                            ▼
                                    EC2 (Docker + compose)
                                    ├─ sssok-app       (GHCR 에서 pull, 헬스체크 후 확정)
                                    └─ sssok-postgres  (볼륨 영속)
```

### 배운 점

- **에러 메시지의 계층을 구분하면 원인 후보를 빠르게 좁힐 수 있다.** `dial tcp timeout`(TCP
  계층)과 `Permission denied (publickey)`(SSH 인증 계층)는 완전히 다른 원인을 가리킨다.
- **인프라 정책(보안 그룹의 IP 화이트리스트)에 맞서 예외를 뚫기보다, 그 정책과 같은 철학으로
  풀 수 있는 방법(self-hosted 러너)을 먼저 찾는 게 이후 유지보수가 쉽다.**
- **에뮬레이션(QEMU)은 "느린 게 아니라, 무거운 연산에서만 느리다."** 컴파일처럼 CPU를 많이
  쓰는 작업은 에뮬레이션 밖으로 빼고, 파일 복사처럼 가벼운 작업만 에뮬레이션 안에 남기는
  식으로 설계하면 크로스 아키텍처 빌드의 비용 대부분을 없앨 수 있다.
- **권한 에러는 디렉터리뿐 아니라 그 안의 개별 파일 소유권도 함께 봐야 한다.**
