# 로컬 모니터링 실행

백엔드는 IDE 또는 Gradle로 실행하고 Prometheus와 Grafana만 Docker Compose로 실행한다.

```bash
# PostgreSQL
docker compose up -d

# Spring Boot
./gradlew bootRun

# 별도 터미널에서 Prometheus와 Grafana
docker compose -f docker-compose.monitoring.yml up -d
```

확인 주소:

- Actuator: <http://localhost:8081/actuator/health>
- Prometheus: <http://localhost:9090>
- Grafana: <http://localhost:3000> (`admin` / `admin`)

Grafana 비밀번호를 바꾸려면 실행 전에 `GRAFANA_ADMIN_PASSWORD` 환경변수를 설정한다. 이 구성은
로컬 대시보드와 Prometheus 알림 상태 확인용이며 Discord 알림은 전송하지 않는다.

서버 배포에서는 Actuator 관리 포트 `8081`을 호스트에 공개하지 않는다. Prometheus는 같은
Docker Compose 네트워크에서 `app:8081`로만 접근한다.

종료할 때는 데이터 볼륨을 유지할지 선택한다.

```bash
# 컨테이너만 종료
docker compose -f docker-compose.monitoring.yml down

# 컨테이너와 로컬 모니터링 데이터까지 삭제
docker compose -f docker-compose.monitoring.yml down -v
```
