# 업로드 내부 트레이싱

## 무엇을 볼 수 있나

업로드 완료 API의 HTTP span 아래에 등록 단계가 나타난다. 새 썸네일 작업에는 등록 당시의
W3C `traceparent`·`tracestate`를 저장한다. HTTP 응답이 끝난 뒤 다른 스레드/서버에서 처리하거나
재시도해도 같은 trace ID로 이어진다. 재시도는 원래 등록 span의 별도 자식으로 나타난다.

브라우저에서 R2로 원본을 전송하는 시간은 백엔드를 거치지 않으므로 이 계측에 포함되지 않는다.

| 구간 | span 이름 | 포함하는 작업 |
| --- | --- | --- |
| 등록 전체 | `upload.registration.total` | 접근 검증부터 완료 응답 생성과 이벤트 발행 |
| 등록 세부 | `upload.registration.<stage>` | 기존 등록 메트릭과 동일: access, validate, file_lookup, storage_head, nickname, registration, folders, file_write, response, created_publish, uploaded_publish |
| HEAD 워커 | `upload.registration.head_worker` | 별도 풀에서 실행하는 파일 검증; 부모 문맥 전달 |
| 후처리 1회 | `upload.processing` | 선점한 작업의 처리, 결과 및 재시도 차수 |
| 사진 다운로드 | `upload.processing.download` | R2 스트림 열기부터 바이트를 모두 읽고 닫기까지 |
| 사진 변환 | `upload.processing.derive` | 방향 판독·디코딩·축소·파생본 인코딩 |
| 변환 세부 | `upload.processing.orientation/decode/resize/encode` | 각 변환 단계; resize/encode는 파생본마다 반복 |
| 사진 메타데이터 | `upload.processing.metadata` | 촬영 시각·GPS 판독; 실제 값은 span에 기록하지 않음 |
| 영상 프레임 | `upload.processing.video_extract` | ffprobe/ffmpeg 실행 및 원격 원본 접근을 합한 시간 |
| 파생본 저장 | `upload.processing.upload_thumbnail/upload_preview` | 썸네일과 프리뷰 각각 R2 업로드 |
| 처리 완료 | `upload.processing.complete` | 완료 트랜잭션과 동기 AFTER_COMMIT 리스너까지 |
| 완료 이벤트 | `upload.processing.ready_read/ready_publish` | 응답 데이터 재조회·URL 생성 / 이벤트 저장 및 발행 |

부모 span과 자식 span은 시간이 겹친다. 전체 시간에 각 단계 시간을 다시 더하면 안 된다.
`complete`가 길면 그 안의 `ready_read`·`ready_publish`를 먼저 비교한다.
사진 파생본 인코딩은 기존처럼 썸네일 → 프리뷰 순서다. 영상 내부의 ffprobe와 ffmpeg는 이번에
별도 span으로 나누지 않았으며, SQL 한 문장마다 자동 span을 만드는 구성도 아니다.

## 계측 코드와 비즈니스 코드의 분리

비즈니스 서비스·HEAD 워커·이미지 처리기는 `UploadTracing`을 주입하거나 호출하지 않는다.
`UploadTracingAspect`가 모니터링 계층에서 대상 메서드를 감싸고, `UploadTracing`이 span과 메트릭을 기록한다.
기존 등록 메트릭 클래스도 트레이싱 의존성 없이 유지한다.

| 업무 경계 | 계측을 적용하는 위치 |
| --- | --- |
| 등록 단계 | 기존 `UploadRegistrationMetrics.measure/total` 외부 호출 |
| 큐 등록 | `ThumbnailJobRepository.enqueue/enqueueAll`; 문맥 캡처·저장 |
| HEAD 스레드 전환 | 전용 실행기의 `submit(Callable)`; Callable만 감싸 취소·purge 유지 |
| 후처리 시도 | `GenerateThumbnailService.generate`; DB 문맥 복원 및 결과 기록 |
| 원본 읽기·파생본 저장 | `ThumbnailMediaStorage`; 다운로드는 스트림 종료까지 포함 |
| 이미지 변환 | `ThumbnailatorImageProcessor`와 `ImageDerivativeOperations`의 외부 호출 |
| 완료 이벤트 본문 | `ReadyMediaReader.read` |
| 완료·이벤트 발행 | 완료 서비스와 `media.ready` 발행 경계 |

큐 데이터에는 DB를 건너기 위한 W3C 문맥 값이 남지만, 도메인은 Tracer나 span API를 알지 않는다.
이미지 처리기는 변환 순서를 결정하고, `ImageDerivativeOperations`는 방향 판독·디코딩·픽셀 변환·인코딩을 담당한다.
계측 없이 직접 생성해도 이미지 처리 로직을 테스트할 수 있다.

Spring 프록시는 같은 객체 안의 메서드 호출을 가로채지 않으므로 내부 메서드에 어노테이션만 붙이지 않았다.
단계를 실제 협력 객체의 경계로 나누고, 완료 계측은 트랜잭션 advice 바깥에서 실행하도록 순서를 지정했다.
메서드 이름이나 경계를 바꾸면 `UploadTracingAspect`의 대상과 계측 테스트도 확인해야 한다.
관련 근거: [Spring 프록시 동작](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html),
[트랜잭션 advice 순서](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/annotations.html).

## 큐 대기와 재시도 구분

`upload.processing`의 `queue.wait.ms`는 **실행 가능 시각(available_at)부터 선점 시각까지**의 대기다.
첫 시도는 등록 후 큐 대기, 재시도는 백오프가 끝난 뒤의 큐 대기다. 백오프 자체와 선점 이후 실행기
대기는 이 값에 포함하지 않는다. 시간축에서 이전 시도와 다음 시도 사이의 간격도 함께 확인한다.
`processing.attempt`는 시도 횟수, `processing.result`는 기존 처리 메트릭의 결과 값이다.

- 기존 작업의 재등록은 저장된 추적 정보와 재시도 이력을 덮어쓰지 않는다.
- 실패 작업을 새로 등록하면 새 요청 문맥을 저장한다.
- 배포 이전 작업이나 복구 작업처럼 문맥이 없으면 독립 트레이스로 시작한다.
- 각 실행의 scope는 정상·예외 모두 닫혀, 재사용하는 워커 스레드에 문맥이 남지 않는다.

## 메트릭으로 먼저 보고 트레이스로 좁히기

기존 `sssok_upload_registration_duration_seconds`, `sssok_media_processing_seconds` 계측을 유지한다.
새 메트릭은 트레이싱이 꺼져 있거나 해당 요청이 샘플링되지 않아도 기록한다.

| 메트릭 | 라벨 | 용도 |
| --- | --- | --- |
| `sssok_upload_processing_stage_seconds` | stage, outcome(success/error) | 후처리 단계별 누적 시간과 호출 수 |
| `sssok_upload_queue_wait_seconds` | kind(image/video), attempt(first/retry) | 실행 가능 시점 이후 큐 대기 |

예: 단계별 최근 5분 평균 소요 시간(초)

```promql
sum by (stage) (rate(sssok_upload_processing_stage_seconds_sum[5m]))
/
sum by (stage) (rate(sssok_upload_processing_stage_seconds_count[5m]))
```

히스토그램 버킷을 추가하지 않았으므로 이 메트릭만으로 p95를 구할 수는 없다.
outcome은 호출의 예외 여부다. 빈 변환 결과나 영상 재시도 결과처럼 정상 반환한 비즈니스 실패는
부모 `processing.result`와 기존 처리 결과 메트릭으로 구분한다. AFTER_COMMIT 예외는 Spring이
처리할 수 있으므로 부모 성공만 보지 말고 하위 span의 오류도 확인한다.

## 적용 순서와 자원 범위

1. #835 / PR #836의 HTTP·OTLP·Tempo 기반을 먼저 병합하고 설정한다.
2. 이 변경을 반영하면 Flyway V57이 큐 테이블에 nullable 컬럼 두 개를 추가한다.
3. dev에서 트레이싱을 켜고 완료 등록 요청을 실행한 뒤, 같은 trace ID에서 등록과 후처리를 확인한다.
4. 운영은 기존 계획대로 5% 샘플링부터 적용한다. 원래 요청의 샘플링 결정은 DB 큐와 재시도에도 유지된다.

이 변경은 Tempo 컨테이너나 서버의 메모리 제한을 바꾸지 않는다. 사진 한 장마다 여러 span이 추가되므로
1단계보다 수집량은 늘어난다. 실제 dev 업로드에서 span 수·Tempo RSS·중앙 서버 MemAvailable을 다시
확인해야 한다. 특히 큰 묶음은 하나의 trace에 span이 많이 모이며, 로컬 합성 데이터 실험만으로
운영 메모리 사용량이 검증된 것은 아니다. 기존 중단 기준(MemAvailable 200MiB 미만 1분 지속,
OOM, 기존 모니터링 장애)을 유지한다.

파일명·저장 경로·서명 URL·사용자 ID·촬영 정보·baggage를 span/메트릭에 추가하지 않는다.
예외는 분류 이름만 기록하며 원본 예외 메시지는 트레이스로 내보내지 않는다.
