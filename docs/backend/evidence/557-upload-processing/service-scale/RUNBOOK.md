# 전체 업로드 트레이스: 이전 → 현재 → JNI 배포 후

이 비교의 대상은 실제 서비스의 업로드 전체 흐름이다. 로컬 JNI/FFM 가공 시간은 별도 보조 자료이며
서비스 완료 시간의 대체값으로 쓰지 않는다. JNI 후보는 아직 배포하지 않았다.

## 비교 단계

1. **이전 측정**: 2026-11-17 새벽 dev, SHA `1d75d361e49b03bcd98ce98b11ce3a123534173b`.
   1명×20장, 등록 평균 787.23ms, created→READY 평균 8,757.95ms, 20/20 READY.
   예전 등록 개선 실험의 전체 행렬은 20/40/60/100/150/200장×1명 및 10장×3/5/10명이다.
2. **현재 재측정**: 같은 dev 배포 버전에서 1명×20/100/200장. 가공 엔진 비교가 아닌 실제 R2 업로드·API·DB 큐·SSE와 서버 trace를 측정한다.
3. **JNI 배포 후 재측정**: 동일한 dev 환경에 승인된 코드가 배포된 뒤 같은 3개 조건을 반복한다.
   운영(prod)에 적용할 경우 prod 자체의 배포 전 기준선을 먼저 확보한다. dev와 prod 차이를 코드 개선율로 해석하지 않는다.

## 고정 조건

- LOAD 인스턴스 → `https://dev-api.ssssok.com`, 전용 테스트 계정·방 사용.
- 원본은 4032×3024 JPEG 6장(p12mp-q-mid)을 순환 사용. 원본 SHA-256을 대조한다.
- 업로더 1명, 1회 요청에 N장 등록, 원본 PUT 동시성 3.
- 기존 `media-upload-x.js` SHA-256: `11aafb104cd903a58939eab872bc5fa57936d9290880180bd9bd7aacbd2dc4e5`.
- UPLOAD_SEC=N+120, READY_TIMEOUT=2N+90초, 등록 요청 제한 10초, ITERATIONS=1.
- dev 컨테이너 1536MiB, Java `-Xmx512m`, 이미지 워커 4, AI 활성화, tracing sampling 1.0.
- API·워커 인스턴스 분리 및 API 사양 축소는 이 전후 비교가 끝난 다음 각각 별도 단계로 측정한다.
- 이번 재측정은 서버를 재시작하지 않았다. 이전 20장 측정은 재시작 후 실행했으므로 온도/캐시 차이가 있다.
- 케이스마다 전용 방과 입력을 준비하고 기존 cleanup을 실행한다. seed 10장 trace는 본 측정에서 제외한다.
- 기존 SSE 종료 관찰 시간을 유지하므로 실제 업로드가 끝나도 k6가 수분 동안 유지된다.
  이 실행 전체 시간을 READY 지연이나 이미지 처리 시간으로 표시하지 않는다.

## 한 표로 연결할 지표

| 경계 | 자료 | 기록 항목 |
| --- | --- | --- |
| 업로드 URL 발급 | k6 upload_issue | 요청 시간, 발급/거절 수 |
| 클라이언트 → R2 원본 PUT | k6 storage_put | 평균/p95/max, 성공 수 |
| 완료 등록 API | k6 upload_complete + registration trace | 응답 시간, HEAD/검증/DB 저장/이벤트 단계 |
| DB 작업 대기 | processing span의 queue.wait.ms | 평균/p95/max, 처리 건수 |
| R2 GET | upload.processing.download | 평균/p95/max |
| 이미지 생성 | derive 및 하위 span | 가공 전체와 decode/resize/encode |
| 파생본 R2 PUT | upload_thumbnail / upload_preview | 각 평균/p95/max, 실패 여부 |
| 완료 및 통지 | complete / ready_read / ready_publish | 각 지연 |
| 사용자 완료 | SSE media.created → media.ready | READY 수, 평균/p95/max, 누락/시간 초과 |
| 자원·안정성 | 앱/Tempo 수집기 | CPU, 메모리, OOM, 재시작, 큐 적체 |

원본 PUT은 클라이언트가 R2로 직접 수행하므로 서버 span이 아니라 k6 측정으로 연결한다.
HTTP 등록과 비동기 후처리는 같은 trace의 연결을 확인한다. trace ID·실행 시각·SHA·표본 수를 반드시 같이 기록한다.
parent/child span 및 병렬 구간을 전부 합산하지 않는다. 인코딩·리사이즈는 이미지당 두 개의 span이 생길 수 있다.
현재 JNI 후보는 derive 경계 전체를 측정하며 C 내부 decode/resize/encode span은 생성하지 않는다.
배포 후 해당 하위 span이 없으면 **미계측**으로 표시하고 0ms로 기록하지 않는다.

## 완료 검증

- 예상 N = 등록 N = created N = READY N, timeout/누락/실패 0인지 확인한다.
- 등록 요청 하나의 서버 trace에서 processing N개와 성공 N개를 확인한다.
- seed용 10장 요청이나 다른 사용자의 trace를 포함하지 않는다.
- trace가 덜 수집됐으면 뒤에 다시 조회하고 누락을 명시한다.
- 이미지가 준비된 뒤 추가 관찰·cleanup·헬스 확인까지 끝나야 해당 케이스를 완료 처리한다.
- 실패 또는 헬스 비정상 시 다음 큰 케이스로 진행하지 않는다.

## 자료 위치

- LOAD: `/home/ec2-user/upload-test/tracing-scale-20261117`
- dev/monitor: `/tmp/upload-trace-scale-20261117`
- 원본 k6 결과에는 setup 데이터가 있으므로 그대로 공개 문서에 복사하지 않는다. 지표와 버전 등 허용한 필드만 추출한다.
- 로컬 요약에는 비밀키·토큰·원본 사진을 포함하지 않는다.

초기 20장 실행의 자원 수집기는 문자열 개행 오류로 시작에 실패했고 실행 도중 수정했다.
20장 초반 CPU/메모리 peak는 완전한 값으로 주장하지 않는다. k6 및 Tempo trace 수집에는 영향을 주지 않았다.
