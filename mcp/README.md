# 사진 분류 MCP 서버

방 전체 사진을 사람별로 분류하는 Python 서버다. 얼굴로 사람 기준을 만들고 전신 특징으로
누락을 보완한다. MCP는 분석 결과를 반환하며, 실제 서비스 폴더 변경은 Spring이 사용자 승인 후 담당한다.

## 읽는 순서

| 순서 | 문서·코드 | 확인할 내용 |
|---|---|---|
| 1 | 이 문서 | 전체 흐름과 현재 구현 범위 |
| 2 | [분류 엔진](servers/gallery/pipeline/photo_analysis.py) | 사진 준비 → 얼굴 그룹 → 신체 보완 → 결과 |
| 3 | [서버 계약](SERVER_CONTRACT.md) | 요청·결과·저장 책임·실패 처리 |
| 4 | [EC2 배포 안내](deployment/README.md) | 수동 CI/CD·배포 검사·복구 |

## 현재 구현

```text
Spring (서비스 연동은 후속)
  │ 방 권한·전체 사진 목록·작업 ID 확정
  ▼
MCP 도구 접수 → 실행 영수증 저장 → 대기 작업 처리
                                    │
                                    ▼
                          별도 워커에서 사진 다운로드
                          얼굴 분류 → 전신 특징 보완
                                    │
                                    ▼
                          결과 보존·임시 분석 파일 정리
                                    │
                                    ▼
                          Spring으로 전달·실패 시 재전송
```

- 사진 분류: YuNet·SFace·SSDLite·OSNet, 그룹별 사진 ID·미분류·오류 반환.
- 서버: 인증·입력 검사, 비동기 시작·조회, 단일 워커, 중복 요청·재시작 처리.
- 저장: Python은 임시 실행 영수증만 보관한다. SQLite와 RDS 직접 접근은 사용하지 않는다.
- 배포: ARM64 Docker, GitHub Actions 검사·수동 배포, 작업 종료 대기·연결 검사·롤백.
- 결과 반영: 분석 완료와 사용자 승인은 별개다. MCP에는 서비스 폴더 변경 도구가 없다.

## 코드 지도

| 위치 | 역할 |
|---|---|
| `servers/gallery/pipeline/photo_analysis.py` | 분석 전체 순서 |
| `servers/gallery/pipeline/image_features.py` | 사진 변환·얼굴/몸 검출·특징 추출 |
| `servers/gallery/pipeline/person_grouping.py` | 사람 그룹 생성·후보 연결 정책 |
| `servers/gallery/pipeline/vendor/` | OSNet 모델 구조·라이선스 |
| `servers/gallery/gallery_mcp/tools/people.py` | MCP 시작·조회 도구 |
| `servers/gallery/gallery_mcp/person_grouping_service.py` | 접수·대기·워커 실행·배포 중 접수 차단 |
| `servers/gallery/gallery_mcp/analysis_worker.py` | 사진 준비·엔진 호출·서비스 응답 변환 |
| `servers/gallery/gallery_mcp/execution_store.py` | 실행 영수증·상태·결과 보관 |
| `servers/gallery/gallery_mcp/result_delivery.py` | 진행·결과 전송·수신 확인·재전송 |
| `servers/gallery/gallery_mcp/inputs.py` | 입력 검증·다운로드 |
| `servers/gallery/gallery_mcp/server.py`, `config.py` | HTTP·인증·설정 |
| `servers/gallery/tests/` | 서버·분류 규칙·결과 전달 검증 |
| `deployment/` | EC2 실행·배포·복구와 배포 테스트 |
| `compose.yml`, `.env.example` | 로컬 빌드·실행 설정 |

`pipeline/run_local.py`는 분석기를 직접 실행하고 지정한 새 출력 폴더에 결과를 보관한다.
`result.json`은 분류 결과, `manifest.json`은 입력·모델 정보, `evidence.json`은 분류 근거,
`job.json`은 실행 상태·소요 시간을 담는다. HTML 보고서 도구는 저장소 밖 로컬에 보관한다.
로컬 CLI 결과는 자동 삭제하지 않는다. 서버는 최종 결과를 실행 기록 JSON에 저장한 뒤
이미지·특징·상세 근거 등 분석 임시 파일을 정리한다. 실행 기록은 수신 확인 전까지 유지하고,
수신 확인 후 기본 24시간이 지나면 정리한다. 서비스 장기 보관은 후속 Spring DB 연동에서 담당한다.

## 실행

```bash
cd mcp
cp .env.example .env
```

설정값:

| 설정 | 의미 |
|---|---|
| GALLERY_TOKEN | MCP 호출 인증용, 무작위 32자 이상 |
| GALLERY_MODELS_DIR | 준비한 4개 모델의 절대 경로 |
| GALLERY_DOWNLOAD_HOSTS | 정확한 R2 S3 API 호스트 이름, 쉼표 구분 |
| GALLERY_BACKEND_URL | Spring의 고정 HTTPS 이벤트 수신 URL |
| GALLERY_BACKEND_TOKEN | Spring 내부 API용 별도 토큰, 32자 이상 |
| GALLERY_STANDALONE | Spring 없이 시험할 때만 true; 기본 false |

모델은 Git과 이미지에 포함하지 않는다.

```bash
python servers/gallery/scripts/prepare_models.py --source /path/to/assets --output /path/to/models
docker compose --profile gallery up --build -d
curl http://127.0.0.1:8818/healthz
curl http://127.0.0.1:8818/readyz
```

모델 출처·고지는 [THIRD_PARTY.md](servers/gallery/THIRD_PARTY.md)를 참고한다. healthz는 프로세스, readyz는 모델 파일 존재를 확인하며 Spring의 가용성이나 모델 실행 성공까지 보장하지 않는다.

Compose는 localhost 바인딩, ARM64, 2 CPU·1400MiB·추가 스왑 없음, 비루트·읽기 전용 루트 파일시스템이다. 원격 서버 연결 시 TLS·프라이빗 네트워크·허용 Host를 구성한다. 토큰은 브라우저에 전달하지 않는다. 직접 Python 실행 시 `GALLERY_TOKEN_FILE`, `GALLERY_BACKEND_TOKEN_FILE`도 지원한다.

`/data`는 실행 입력·미전송 결과의 임시 볼륨이다. 서비스 작업 이력의 기준은 Spring/RDS다. 볼륨을 없애는 재시작은 미전송 결과를 잃으므로 Spring이 실행 기한을 확인하고 재요청해야 한다.

## 클라이언트

```bash
GALLERY_TOKEN_FILE=/secure/token python servers/gallery/scripts/client.py \
  --endpoint http://127.0.0.1:8818/mcp --room-id 123 \
  --job-id 'Spring작업UUID' --request-id '실행UUID' --attempt 1 \
  --photos /private/photos.json --output /private/result.json
```

photos.json은 mediaId·source·extension 배열이다. `--execution-id`로 조회를 재개한다. 타임아웃은 서버 작업 취소를 의미하지 않는다.

## 로컬 분석과 테스트

MCP 서버의 로컬 사진 입력은 기본 비활성화이며 `GALLERY_INPUT_ROOT`로 활성화한다.
관리자가 관리하는 입력 루트 아래의 상대 경로와 일반 파일만 허용하고, `..` 및 경로 중간·파일의
심볼릭 링크는 거부한다. 실제 복사 시에도 디렉터리 파일 디스크립터 기준으로 열어 경로 교체를 방어한다.
입력 루트와 그 상위 디렉터리는 신뢰할 수 있는 관리자가 관리해야 한다.

아래 명령은 저장소 루트에서 실행한다. 로컬 CLI는 결과 폴더를 자동 삭제하지 않는다.
이미 존재하는 출력 폴더는 덮어쓰지 않으며 HTML 보고서는 생성하지 않는다.

```bash
cd mcp/servers/gallery
python -m pipeline.run_local --input /path/to/photos --output /path/to/new-result \
  --assets-root /path/to/models --threads 2
python -m unittest discover -s tests -v
```

의존성이 설치된 환경에서 실행한다. 모델은 체크섬 파일과 일치하는 가중치를 별도로 준비한다.
`photo_analysis.py`의 POLICY는 분류 기준을 기록한 메타데이터이며 실제 기준값과 별도로 관리된다.

## 검증 상태

2026-11-02 기준. 상세 측정 원자료는 저장소 밖 로컬에 보관한다.

- 현재 소스로 서버·분류·JSON 저장 테스트 51개와 배포 제어·복구 테스트 9개 통과.
  배포 복구 테스트는 Docker 명령을 모의 실행한 것으로 실제 EC2 롤백 검증은 아니다.
- 로컬 Docker ARM64에서 개발 사진 62장 분석: 5그룹·74개 연결·오류 0, 기존 그룹별 사진 구성 유지.
  같은 개발 사진의 목표 연결 80건 중 74건이며 새로운 사진에 대한 일반 정확도는 미검증이다.
- 실제 분석 워커와 테스트 수신기로 503 후 결과 재전송, 수신 확인, 중복 요청, 재시작 조회와
  임시 파일 정리를 확인했다. 별도 HTTP 서버의 인증·MCP 연결 검사도 통과했다.
- CPU 2개·메모리 제한 1400MiB에서 몸 특징 추출 배치 16·8·4를 각각 두 번 비교했다.
  배치 8의 최대 메모리는 약 1054MiB, 전체 통합 검사 시간은 33.26~36.37초이며 분류 결과는 동일했다.
  시간에는 상태 조회·콜백 검사가 포함되고 메모리에는 파일 캐시가 포함된다. 기본 배치는 8이다.
- 실제 GitHub Actions·GHCR·EC2 배포, R2 다운로드, Spring/RDS 연동과 EC2 성능·복구는 검증 전이다.

## 후속 범위

Spring/RDS 작업·결과·승인 API와 실제 갤러리 연결, 재실행 시 기존 사람 폴더 매칭,
대량 사진·다중 워커는 후속이다. 현재 시험 한도는 100장이며 초과 시 전체 요청을 거절한다.

장면·중복 분류와 대화형 정리도 이후 확장한다. 운영 MCP는 갤러리와 별도 권한·프로세스로
관리자 페이지의 릴리스·배포 지원과 장애 조사를 제공하는 계획이며, 실행 서버는 아직 없다.
중앙 허브도 구현되지 않았다.

## 저장소에 포함하지 않는 자료

시연 갤러리 앱·원본 사진·모델 가중치·시연 DB·결과 보고서·토큰·복구 백업은 로컬에 보관한다.
모델 준비 방법·체크섬·라이선스와 사진 없이 실행 가능한 테스트는 저장소에 유지한다.
조직 AWS 정책·상세 검증 기록·과거 실험 문서·초기 계획·오프라인 배포 도구는 로컬에 보관한다.
