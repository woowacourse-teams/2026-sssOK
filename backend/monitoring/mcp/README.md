# MCP 모니터링 이전 준비 (#710)

상태: 2026-11-05 새 MCP(`10.0.0.251`) 적용 완료. 중앙 node·Alloy UP, Loki gateway 로그 저장,
중앙 외 접근 및 X-Forwarded-For 위조 403을 확인했다.
앱 소스는 `mcp/servers/gallery`, 배포 코드는 `mcp/deployment`에 있다.
기존 서버에서 `/healthz` 정상 응답과 SQLite v2 작업 기록을 확인했다.
앱 계측은 아직 구현하지 않았다.

## 제공 범위

- 독립 Compose 프로젝트로 node-exporter, Alloy, 읽기 전용 Docker API 프록시, 지표 Nginx 실행.
- 호스트 CPU·메모리·스왑·디스크 지표 및 Alloy 전송·폐기 지표.
- AMI 기본 수집기와 서비스는 변경하지 않으며 9100은 호스트에 게시하지 않는다.
- 19100은 필수 MONITORING_PRIVATE_IP에 바인딩하고 실제 중앙 소스 IP만 허용한다.
- com.sssok.log.service 라벨을 가진 Docker 컨테이너만 수집한다. 앱은 sssok-gallery-mcp, gateway는 sssok-mcp-gateway다.
- 앱에 라벨을 추가하려면 컨테이너 재생성이 필요하므로 앱 이전 시 반영한다. 호스트 프로세스 로그는 별도 연결 필요.
- 외부 앱 로그의 비밀값 마스킹은 아직 검증하지 않았다. 원문에 토큰·사용자 데이터가 없는지 확인한 뒤 라벨을 적용한다.
- Alloy 컨테이너 조회 권한은 환경변수를 읽을 수 있으므로 API 네트워크는 내부망으로 격리한다.

## 적용 순서

1. monitoring/mcp와 monitoring/alloy 디렉터리를 상대 경로 그대로 복사한다.
2. monitoring.env.example을 monitoring.env로 복사하고 새 사설 IP를 입력한다.
3. docker compose --env-file monitoring.env -f compose.yml config -q 후 up -d 실행.
4. 중앙 nginx.conf의 loki_sender_allowed에 새 MCP IP를 추가하고 문법 검사 후 reload한다. 기존 허용 IP는 보존한다.
5. 중앙 Prometheus의 기존 node와 alloy job 각각의 static_configs에 아래 항목을 추가한다. IP 자리표시자는 실제 새 MCP IP로 바꾼다.

```yaml
- targets: [NEW_MCP_PRIVATE_IP:19100]
  labels:
    environment: mcp
    server: mcp
```

node job은 /metrics, alloy job은 /alloy-metrics 경로를 사용한다.
중앙 rules.yml도 함께 반영한다. 호스트 자원·node 수집 중단·Alloy 전송 및 폐기 알림에 mcp를 포함했다.
기존 호스트 대시보드의 환경 선택에서 mcp 지표를 조회한다.

6. 중앙에서 두 지표 경로 HTTP 200, Prometheus 두 target UP, Loki에서 environment=mcp 로그 실제 저장을 확인한다.
7. 중앙 외 호스트에서 지표 경로 403을 확인한다. X-Forwarded-For 위조로도 허용되지 않아야 한다.

## 롤백

독립 프로젝트의 docker compose --env-file monitoring.env -f compose.yml stop으로 수집기만 중지한다.
앱과 우테코 수집기는 이 Compose에 포함되지 않는다. down -v는 사용하지 않는다.
중앙 target은 백업 설정으로 복원 후 검사·reload한다.

## 앱 관측 범위

- Docker 앱의 /healthz·/readyz와 서비스 토큰 인증, SQLite v2 및 모델 이전을 검증했다.
- 호스트 자원·로그 전송을 수집한다. 앱 요청/오류/지연/대기/실행 작업 수의 별도 지표 계측은 아직 없다.
- 재부팅 후 앱·gateway·runner·수집기 자동 복구, ALB healthy, 공개 MCP 인증과 지표·로그 복구를 검증했다.
- 생성한 테스트 이미지로 유사 사진·인물 분석 파이프라인을 실행했다. 실제 사용자 사진·백엔드 콜백을 포함한 전체 흐름은 별도 검증 항목이다.
