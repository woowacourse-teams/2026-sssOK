# 사진 분류 MCP 서버

방 전체 사진을 사람별로 분류하는 Python 서버다. 얼굴로 사람 기준을 만들고 전신 특징으로
누락을 보완한다. MCP는 분석 결과를 반환하며, 실제 서비스 폴더 변경은 Spring이 사용자 승인 후 담당한다.

## 읽는 순서

| 순서 | 문서·코드 | 확인할 내용 |
|---|---|---|
| 1 | 이 문서 | 전체 흐름과 현재 구현 범위 |
| 2 | [분류 엔진](servers/gallery/pipeline/photo_analysis.py) | 사진 준비 → 얼굴 그룹 → 신체 보완 → 결과 |
| 3 | [서버 계약](SERVER_CONTRACT.md) | 요청·결과·저장 책임·실패 처리 |
| 4 | [서버 실행 안내](servers/gallery/README.md) | 로컬 실행·설정·테스트 |
| 5 | [EC2 배포 안내](deployment/README.md) | 수동 CI/CD·배포 검사·복구 |

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
| `evidence/644/` | 로컬 ARM64 분석·결과 전달 검증 기록 |

`pipeline/run_local.py`는 분석기를 직접 실행하고 지정한 새 출력 폴더에 결과를 보관한다.
`result.json`은 분류 결과, `manifest.json`은 입력·모델 정보, `evidence.json`은 분류 근거,
`job.json`은 실행 상태·소요 시간을 담는다. HTML 보고서 도구는 저장소 밖 로컬에 보관한다.
로컬 CLI 결과는 자동 삭제하지 않는다. 서버는 최종 결과를 실행 기록 JSON에 저장한 뒤
이미지·특징·상세 근거 등 분석 임시 파일을 정리한다. 실행 기록은 수신 확인 전까지 유지하고,
수신 확인 후 기본 24시간이 지나면 정리한다. 서비스 장기 보관은 후속 Spring DB 연동에서 담당한다.

## 검증 상태

- 2026-11-02 로컬 Python 테스트: 서버·분류·JSON 저장 51개, 배포 제어·복구 9개 통과.
  JSON 결과·근거·상태 저장, 실패 기록과 기존 결과 보호를 고정된 모델 출력으로 검증했다.
  배포 복구는 Docker 명령을 모의 실행한 검증이며 실제 EC2 교체 결과가 아니다.
- [2026-10-30 ARM64 검증](evidence/644/deployment-preparation.json): 62장, 5그룹, 74개 연결,
  오류 0. 당시 서버 테스트는 41개였으며 이후 배포 코드의 이미지 검증을 의미하지 않는다.
- [결과 전달 검증](evidence/644/python-callback-verification.json): 테스트 수신기 503 이후 재전송,
  수신 확인·임시 파일 정리·재시작 후 결과 조회 확인.
- [리팩터링 당시 ARM64 검증](evidence/644/refactor-verification.json): 새 이미지 안에서 45개 테스트 통과,
  실제 62장 재분석의 그룹별 사진 구성이 기존과 동일했다. 5그룹·74연결·오류 0,
  재전송·중복 요청·재시작 조회·파일 정리 및 별도 HTTP 서버 연결 검사를 통과했다.
  통합 검사 약 36.4초, 최대 메모리 약 1379MiB로 1400MiB 제한에 가까웠다. EC2 여유는 별도 측정한다.
- [몸 특징 추출 배치 비교](evidence/644/batch-memory-comparison.json): 16·8·4를 각각 두 번 실행했다.
  배치 8에서 최대 메모리 약 1054MiB, 62장의 그룹별 사진 구성 동일. 기본 배치를 8로 변경했다.
  배치 4도 전체 최대치는 비슷했으며 다음 메모리 개선 대상은 얼굴 검출 단계다.
- 같은 개발 사진의 목표 연결 80건 중 74건을 회수했다. 새로운 사진에 대한 일반 성능은 미검증이다.
- 현재 CI/CD의 실제 GitHub Actions 실행·EC2 배포, 실제 R2 입력·EC2 성능·복구는 검증 전이다.

## 후속 범위

Spring/RDS 작업·결과·승인 API와 실제 갤러리 연결, 재실행 시 기존 사람 폴더 매칭,
대량 사진·다중 워커는 후속이다. 현재 시험 한도는 100장이며 초과 시 전체 요청을 거절한다.

장면·중복 분류와 대화형 정리도 이후 확장한다. 운영 MCP는 갤러리와 별도 권한·프로세스로
관리자 페이지의 릴리스·배포 지원과 장애 조사를 제공하는 계획이며, 실행 서버는 아직 없다.
중앙 허브도 구현되지 않았다.

## 저장소에 포함하지 않는 자료

시연 갤러리 앱·원본 사진·모델 가중치·시연 DB·결과 보고서·토큰·복구 백업은 로컬에 보관한다.
모델 준비 방법·체크섬·라이선스와 사진 없이 실행 가능한 테스트는 저장소에 유지한다.
과거 실험 문서·초기 계획·오프라인 배포 도구는 현재 실행 경로와 분리해 로컬에 보관한다.
