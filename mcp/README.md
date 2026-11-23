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
  신체 보완은 서로 다른 기준 사진 2장 이상으로만 연결한다. 기준 사진이 1장인 경쟁 인물도
  점수 차이 비교에는 포함하며, 더 비슷하거나 차이가 작으면 보류한다.
  OSNet은 얼굴에 연결된 기준 신체와 품질 조건을 통과한 미연결 후보만 계산한다.
  기준 또는 후보가 없으면 신체 특징 추론을 생략한다(#852).
  로컬 ARM64 2 CPU·1400MiB 검증에서는 워커 재사용 단독 대비 41장 8.78→7.89초,
  62장 12.48→12.22초였다(각 모드 최초 1회 제외, 반복 3회 중앙값).
  신체 특징 구간은 각각 1.445→0.002초, 3.066→2.514초로 줄었고 계산 대상은 41→0개, 85→68개였다.
  총 16회에서 그룹별 사진·분류 방식·건수·미분류·오류가 같았다. 유사도 점수의 비트 단위 일치는
  이 비교 기준에 포함하지 않았다. 최대 관측 컨테이너 메모리는 결합 시 각각 911MiB, 942MiB였다.
  62장의 전체 시간 이득은 약 2%로 작고 표본 수도 적으므로 운영 성능 보장으로 해석하지 않는다.
  [측정 요약](servers/gallery/evaluation/body-feature-selection-852.json)을 참고한다.
  단독 갤러리 테스트 199개와 #851 결합 테스트 208개가 통과했다.
- 서버: 인증·입력 검사, 비동기 시작·조회, 단일 워커, 중복 요청·재시작 처리.
- 저장: Python은 SQLite에 실행 상태·결과·전송 확인을 임시 보관한다. RDS에 직접 접근하지 않는다.
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
`job.json`은 실행 상태·소요 시간을 담는다.
`evidence.json`의 `computed_body_feature_ids`가 실제 계산한 신체 ID다. `body-features.npz`는
기존 신체 ID 인덱스를 유지하며 미계산 행은 0이고, 미계산 신체의 crop 이미지는 만들지 않는다.
외부 분석 도구는 이 ID 목록으로 계산한 특징만 선택해야 한다. HTML 보고서 도구는 저장소 밖 로컬에 보관한다.
로컬 CLI 결과는 자동 삭제하지 않는다. 서버 워커는 최종 결과와 완료 상태를 SQLite에 함께 커밋한 뒤
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
| GALLERY_PROGRESS_INTERVAL_SECONDS | 진행이 바뀐 동안 Spring에 보내는 최소 간격(초); 기본 2 |
| GALLERY_HEARTBEAT_INTERVAL_SECONDS | 진행이 그대로일 때 생존 확인을 보내는 간격(초); 기본 15. 진행 간격보다 짧을 수 없다 |

모델은 Git과 이미지에 포함하지 않는다.

```bash
python servers/gallery/scripts/prepare_models.py --source /path/to/assets --output /path/to/models
docker compose --profile gallery up --build -d
curl http://127.0.0.1:8818/healthz
curl http://127.0.0.1:8818/readyz
```

모델 출처·고지는 [THIRD_PARTY.md](servers/gallery/THIRD_PARTY.md)를 참고한다. healthz는 필수 백그라운드 작업의 장애 상태, readyz는 해당 상태와 모델 파일 존재를 확인하며 Spring의 가용성이나 모델 실행 성공까지 보장하지 않는다.

Compose는 localhost 바인딩, ARM64, 2 CPU·1400MiB·추가 스왑 없음, 비루트·읽기 전용 루트 파일시스템이다. 원격 서버 연결 시 TLS·프라이빗 네트워크·허용 Host를 구성한다. 토큰은 브라우저에 전달하지 않는다. 직접 Python 실행 시 `GALLERY_TOKEN_FILE`, `GALLERY_BACKEND_TOKEN_FILE`도 지원한다.

`/data`는 실행 입력·미전송 결과의 임시 볼륨이다. 서비스 작업 이력의 기준은 Spring/RDS다. 볼륨을 없애는 재시작은 미전송 결과를 잃으므로 Spring이 실행 기한을 확인하고 재요청해야 한다.

실행 기록은 `/data/executions-v1.sqlite3`에 저장한다. 부모가 결과 파일을 받아 완료를 기록하는
방식 대신 워커가 DB에 직접 완료를 커밋하므로, 커밋 직후 서버가 종료되어도 결과가 유지된다.
기존 JSON 실행 기록은 시작 시 한 번만 이전하며 원본을 보존한다. 스키마·이전·백업·재시작 계약은
[SERVER_CONTRACT.md](SERVER_CONTRACT.md#sqlite-저장과-트랜잭션)를 참고한다.

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

- 초기 JSON 저장 구현에서 서버·분류 테스트 51개와 배포 제어·복구 테스트 9개 통과.
  배포 복구 테스트는 Docker 명령을 모의 실행한 것으로 실제 EC2 롤백 검증은 아니다.
- SQLite 교체 후 새 ARM64 이미지에서 CI와 동일한 마운트 구성으로 서버·분류·저장소 테스트
  78개 통과. 배포 테스트 17개는 macOS·Linux에서 통과했다. 별도 프로세스의 커밋 전후 종료,
  동시 접수·선점, JSON 이전, 결과 재전송을 검증했다. 실제 사진 추론·EC2 이전 검증과는 구분한다.
- 로컬 Docker ARM64에서 개발 사진 62장 분석: 5그룹·74개 연결·오류 0, 기존 그룹별 사진 구성 유지.
  같은 개발 사진의 목표 연결 80건 중 74건이며 새로운 사진에 대한 일반 정확도는 미검증이다.
- 실제 분석 워커와 테스트 수신기로 503 후 결과 재전송, 수신 확인, 중복 요청, 재시작 조회와
  임시 파일 정리를 확인했다. 별도 HTTP 서버의 인증·MCP 연결 검사도 통과했다.
- CPU 2개·메모리 제한 1400MiB에서 몸 특징 추출 배치 16·8·4를 각각 두 번 비교했다.
  배치 8의 최대 메모리는 약 1054MiB, 전체 통합 검사 시간은 33.26~36.37초이며 분류 결과는 동일했다.
  시간에는 상태 조회·콜백 검사가 포함되고 메모리에는 파일 캐시가 포함된다. 기본 배치는 8이다.
- 이전 JSON 구현은 GitHub Actions CI를 통과했다. SQLite 변경은 별도 검증 대상이며,
  GHCR·EC2 배포, R2 다운로드, Spring/RDS 연동과 EC2 성능·복구는 검증 전이다.

## 후속 범위

Spring/RDS 작업·결과·승인 API와 실제 갤러리 연결, 재실행 시 기존 사람 폴더 매칭,
대량 사진·다중 워커는 후속이다. 현재 한도는 200장(Spring 의 gallery.organization.job.max-photos 와 같아야 한다)이며 초과 시 전체 요청을 거절한다.

장면 분류와 대화형 정리도 이후 확장한다. 운영 MCP는 갤러리와 별도 권한·프로세스로
관리자 페이지의 릴리스·배포 지원과 장애 조사를 제공하는 계획이며, 실행 서버는 아직 없다.
중앙 허브도 구현되지 않았다.

## 저장소에 포함하지 않는 자료

시연 갤러리 앱·원본 사진·모델 가중치·시연 DB·결과 보고서·토큰·복구 백업은 로컬에 보관한다.
모델 준비 방법·체크섬·라이선스와 사진 없이 실행 가능한 테스트는 저장소에 유지한다.
조직 AWS 정책·상세 검증 기록·과거 실험 문서·초기 계획·오프라인 배포 도구는 로컬에 보관한다.

분류 정책 1.0.1-local은 단일 신체 기준을 가진 경쟁 인물도 비교에 포함한다.
위 62장·74개 연결 수치는 이 정책 변경 전 측정이며 변경 후 실제 사진 회수율은 미검증이다.

## 중복 사진 분류 (#661)

`group_similar_photos` 도구와 `--kind SIMILAR` 로컬 실행·클라이언트를 추가했다.
DINOv2+ORB 및 중앙 이미지 ResNet50 비교로 최소 3장 그룹과 2장 후보·단독 사진을 반환한다.
두 작업 종류는 단일 워커를 공유한다. 추가 모델 파일과 SQLite schema v2 이전이 필요하다.
구현 정책·실제 자원 측정·미검증 범위는 [SIMILAR_PHOTOS.md](SIMILAR_PHOTOS.md)를 참고한다.
기존 위 검증 기록은 인물 분류 도입 시점의 기록이다.

## 분석 워커 재사용

한 프로세스가 한 번에 한 앨범을 분석한다. 얼굴·신체 검출과 유사 사진의 장면·중심 특징 추출은
10장마다, 또는 해당 단계의 마지막 사진에서 실행권을 양보한다. 작업별 스레드는 양보 지점에서
대기하므로 중간 특징과 호출 상태를 보존한다. 최종 그룹은 앨범 전체로 계산한다.

- 실행 중인 앨범을 포함해 두 건까지 메모리에 둔다. 자리가 생기면 접수 순서로 한 건을 가져온다.
  세 번째 앨범은 상주 작업 하나가 끝날 때까지 SQLite에서 기다린다.
- 배정받은 실행 시간의 누적이 2초에 도달하면 다음 양보 지점에서 일반 큐로 이동한다.
  상위 큐부터 선택하고 같은 큐에서는 차례대로 실행한다. 일반 큐에서 연속 30초를 기다리면
  다음 선택 시 상위 큐로 복귀한다. 작업 시간 제한에서도 다른 앨범에 양보한 시간은 제외한다.
- 작업별 로그와 결과 디렉터리를 분리한다. 분류 오류가 나면 상주한 다른 작업을 마친 후 프로세스를
  교체한다. 시간 초과·프로세스 종료 시에는 커밋된 결과를 보존하고 중단된 상주 작업을 실패 처리한다.

- `GALLERY_WORKER_MAX_JOBS`: 기본 20. 지정한 작업 수 후 프로세스를 재생성한다. 1이면 매 작업 후 종료한다.
- `GALLERY_WORKER_IDLE_SECONDS`: 기본 60. 대기열이 비어 있는 동안 유휴 시간이 지나면 메모리를 반환한다.
- 최초 요청과 재생성 직후 요청에는 초기화 비용이 다시 든다. 반복 요청의 개선율과 구분해서 측정한다.
- 최대 작업 수와 유휴 종료는 메모리 상한을 보장하지 않는다. 배포 전 격리된 ARM64 환경에서
  실제 모델의 최대 RSS·컨테이너 메모리·연속 요청 결과를 확인한다.

오프라인 비교는 모델이 준비된 Python 환경에서 다음처럼 실행한다. 출력 디렉터리는 새 경로여야 한다.
기존 단발 워커 3회와 재사용 워커 6회(최초 1회 + 반복 5회)를 순서대로 실행하며,
분류 결과 JSON을 저장한 뒤 실제 서비스의 중간 파일 정리를 수행한다.

```bash
python mcp/servers/gallery/scripts/benchmark_worker.py \
  --input /path/to/photos41 --input /path/to/photos62 \
  --assets /path/to/models --output /path/to/new-benchmark
```

`results.json`의 `same_full_result`는 그룹·멤버십(점수 포함)·건수·미분류·오류의 완전 일치다.
RSS와 컨테이너 메모리는 100ms 간격 표본 최대값이며, 컨테이너 메모리는 Linux cgroup v2에서만
수집한다(수집 불가 시 0). 로컬 파일 복사부터 결과 저장·중간 파일 정리까지 측정하므로
실제 R2 다운로드·서버 대기열·콜백·SSE를 포함한 사용자 대기 시간과는 다르다.
공용 서버 대신 별도 ARM64 컨테이너에서 CPU·메모리 제한을 걸고 실행한다.

### 워커 재사용 검증 (#851, 2026-11-17)

파이프라인 `1.1.6-exposure-candidate`의 동일한 코드를 단발/재사용 모드로 비교했다.
로컬 ARM64 Docker, 2 CPU, 1400MiB 제한, 네트워크 차단, 정상 중간 파일 정리 조건이다.

| 사진 | 단발 3회 중앙값 | 재사용 최초 | 재사용 이후 5회 중앙값 | 반복 요청 단축 |
|---|---:|---:|---:|---:|
| 41장 | 12.47초 | 12.04초 | 9.65초 | 22.6% |
| 62장 | 16.75초 | 15.05초 | 13.24초 | 21.0% |

18회 모두 그룹·멤버십 점수·건수·미분류·오류가 같은 입력의 기준 결과와 완전히 일치했다.
재사용 모드의 컨테이너 표본 최대 메모리는 각각 974MiB, 982MiB였다(단발은 852MiB, 949MiB).
작업 직후 워커 RSS는 41장 470–633MiB, 62장 563–602MiB로, 프로세스가 종료되는 단발 방식보다
대기 메모리가 증가한다. 6회 반복에서 무제한 메모리 안전성이나 장기 누수 부재를 입증한 것은 아니다.
컨테이너는 정상 종료했고 OOMKilled=false였다. 실제 서버 E2E 성능은 별도 배포 검증이 필요하다.
측정 요약은 [worker-reuse-851.json](servers/gallery/evaluation/worker-reuse-851.json)에 보관한다.

테스트: 갤러리 204개, 배포 23개 통과. 재사용·로그/작업 분리·인물/유사사진 전환·실패/비정상 종료·
타임아웃·유휴 종료·처리 건수 제한·서버 종료·잘못된 응답·DB 커밋 직후 종료를 포함한다.
