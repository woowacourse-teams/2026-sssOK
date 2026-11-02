# Python 갤러리 MCP 분석 서버

방 전체 사진을 분석하는 내부 서버다. Spring이 전체 사진 목록과 접근 권한을 확정하고 Python이 사진 다운로드·분석·결과 전달을 담당한다. RDS를 직접 접근하지 않으며 SQLite를 사용하지 않는다.

[입력·결과·Spring 수신 확인·복구 계약](../../SERVER_CONTRACT.md)을 먼저 참고한다.

## 제공 기능

- Streamable HTTP `/mcp`, 내부 토큰·Host·Origin 검사
- `group_photos_by_person`: Spring 작업 ID·실행 ID·회차·전체 사진 목록 접수
- `get_gallery_job`: 임시 로컬 실행 상태 확인
- 한 번에 하나의 별도 분석 워커, 600초 시간 제한
- 허용된 HTTPS 호스트에서 사진을 스트리밍 다운로드; 로컬 파일은 테스트 설정 시에만 허용
- Spring 고정 내부 API에 진행·결과 전달, 실패 재전송, 저장 확인 후 영수증 만료
- 종료된 작업의 사진·특징·분석 임시 파일 정리

현재 기본 100장 상한은 시험용이다. 넘으면 전체 요청을 거절하며 방 일부만 처리하지 않는다. 사용자 승인·폴더 변경 도구는 없다.

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

모델 출처·고지는 [THIRD_PARTY.md](THIRD_PARTY.md)를 참고한다. healthz는 프로세스, readyz는 모델 파일 존재를 확인하며 Spring의 가용성이나 모델 실행 성공까지 보장하지 않는다.

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

## 서버 코드 읽는 순서

```text
tools/people.py                  시작·조회 요청 접수
  → person_grouping_service.py  입력 검사·작업 접수·워커 실행 관리
    → execution_store.py        실행 영수증·상태·결과 저장
    → analysis_worker.py        사진 다운로드·분류 엔진 호출·mediaId 변환
  → result_delivery.py          백엔드 진행·결과 전달·수신 확인·재전송
```

`server.py`가 인증과 MCP 도구 등록, 작업 처리·결과 전달 루프를 연결한다.
`config.py`는 실행 설정, `inputs.py`는 사진 입력 검증과 다운로드를 담당한다.
워커는 `python -m gallery_mcp.analysis_worker`로 별도 실행된다.
백엔드 URL이 없는 standalone 모드에서는 결과 전달 루프를 실행하지 않고 조회 도구로 확인한다.

## 분류 엔진 읽는 순서

| 파일 | 역할 |
|---|---|
| `pipeline/photo_analysis.py` | 사진 준비 → 얼굴 그룹 → 신체 보완 → JSON 결과 |
| `pipeline/image_features.py` | 사진 변환·모델 실행·얼굴과 몸 특징 추출 |
| `pipeline/person_grouping.py` | 특징을 비교해 사람 그룹 생성·누락 사진 연결 |
| `pipeline/run_local.py` | 터미널에서 분석 실행 |

서버 워커는 `photo_analysis.run()`을 호출한다. 서버 분석에서는 HTML이나 사람별 로컬 폴더를
만들지 않는다. 이미지·특징·상세 근거는 분석 중에만 보관하고, 최종 결과를 실행 기록에
저장한 뒤 작업 디렉터리와 함께 정리한다. `photo_analysis.py`의 POLICY는 현재 기준을 기록하는
메타데이터이며 실제 기준값과의 통합은 별도 작업이다.

로컬에서 JSON 결과를 저장하려면 `servers/gallery` 디렉터리에서 실행한다.

```bash
python -m pipeline.run_local --input /path/to/photos --output /path/to/new-result \
  --assets-root /path/to/models --threads 2
```

지정한 새 출력 폴더에 `result.json`, `manifest.json`, `evidence.json`, `job.json`과
분석 중간 파일을 보관한다. 기존 출력 폴더는 덮어쓰지 않으며 로컬 결과는 자동 삭제하지 않는다.
HTML 보고서는 생성하지 않는다.

## 테스트와 남은 연결

```bash
cd servers/gallery
python -m unittest discover -s tests -v
```

인증·입력 범위·단일 실행·재시작·정리·결과 재전송·수신 확인·만료·분류 규칙을 검증한다. 현재 HTTPS 다운로드는 모의 스토리지 응답으로, 실제 분석은 로컬 사진으로 시험했다. 실제 R2·Spring·RDS 연결 및 EC2 측정은 다음 단계다.
