# 자연어 이미지 검색 (#460)

## 검색 문서와 분석 작업 상태

`media_search_document`는 미디어당 하나의 검색 문서와 분석 작업 상태를 함께 저장한다.
`media_id`가 기본 키이며 원본 미디어 삭제 시 외래 키의 `ON DELETE CASCADE`로 문서도 삭제된다.
기존 미디어 상태와 별도로 관리하여 AI 처리 실패가 업로드 완료를 취소하지 않는다.

| 상태 | 의미 | 다음 상태 |
| --- | --- | --- |
| PENDING | 최초 분석 또는 재시도 대기 | PROCESSING |
| PROCESSING | 워커가 선점하여 처리 중 | READY, PENDING, FAILED |
| READY | 설명·특징·검색 텍스트·벡터 저장 완료 | 이번 MVP에서는 재분석 없음 |
| FAILED | 허용한 시도 횟수 소진 | 자동 재시도 없음 |

`register`는 READY 이미지에만 대기 문서를 생성하고 중복 등록은 무시한다.
`claim`은 조건부 UPDATE로 선점과 시도 횟수 증가를 한 번에 수행한다.
선점 결과의 미디어 ID·시도 번호를 완료/실패 저장에 사용하므로 이전 실행의 늦은 결과는 무시한다.
`fail`은 재시도 시각을 기록하고 시도 상한에 도달하면 FAILED로 바꾼다.
`recover`는 시작 시각이 복구 기준보다 오래된 PROCESSING 작업만 다시 대기시키거나 실패 처리한다.
복구 기준은 외부 호출 제한 시간보다 충분히 길게 설정해야 한다.

워커는 외부 AI 호출 동안 DB 트랜잭션을 유지하지 않고, 등록·선점·결과 저장은 각각 짧은 DB 작업으로 실행한다.
저장은 JPA Entity → JpaRepository → Adapter 구조로 처리한다. 벡터 컬럼은 엔티티 필드로
매핑하지 않고 JPA 네이티브 쿼리의 CAST로 저장한다. 벡터 전체를 작업 상태 조회에 함께 읽지 않는다.
네이티브 갱신은 영속성 컨텍스트를 비워 상태 조회가 오래된 값을 읽지 않도록 한다.
선점 UPDATE와 시도 번호 조회는 같은 짧은 트랜잭션으로 묶는다. H2에 벡터 타입을 흉내 내지 않는다.

## 모델 정보와 벡터 차원

모델 선정 전이므로 현재 컬럼은 차원이 고정되지 않은 `vector`다. 문서마다 임베딩 모델·차원을 저장한다.
READY 상태에서는 필수 분석 정보와 벡터 차원의 일치를 DB 제약으로 검사한다.
비어 있는 벡터·영벡터·NaN·무한대는 결과 객체 생성 시 거절한다.
검색을 구현할 때 현재 모델·차원과 일치하는 문서만 비교해야 한다.

## 업로드 이벤트와 분석 워커

이미지 처리 완료 → 별도 트랜잭션으로 작업 등록 → 전용 실행기에 제출 → 선점 → 설명 생성 → 임베딩 → 결과 저장 순서다.

- `ImageAnalysisTrigger`는 `MediaReadyEvent`의 AFTER_COMMIT 리스너다.
- `ImageAnalysisRegistrar`는 REQUIRES_NEW 트랜잭션으로 등록을 확정한 뒤 반환한다.
- `ImageAnalysisDispatcher`는 전용 실행기에 제출하고, 거절되면 PENDING 작업을 그대로 남긴다.
- `AnalyzeImageService`는 프리뷰가 있으면 프리뷰, 없으면 원본의 GET 서명 URL을 설명 생성 포트에 전달한다.
- `ImageDescriptionPort`와 `TextEmbeddingPort` 구현체는 전달받은 제한 시간을 실제 HTTP 요청에 적용해야 한다.
- `ImageAnalysisSweeper`는 누락된 등록·중단된 작업·재시도 대기 작업을 회수한다.
- DB의 이미지 타입은 IMAGE가 아닌 JPEG·PNG·GIF이므로 해당 타입만 등록·분석한다.

## 활성화와 작업 제한

현재 `media.search.enabled=false`가 기본값이다. 실제 제공자 어댑터가 없으므로 활성화하지 않는다.
실제 활성화에는 두 AI 포트의 구현체와 `media.search.created-since` 설정이 필요하다.

| 설정 | 기본값 | 목적 |
| --- | --- | --- |
| enabled | false | 이벤트·워커·배치 전체 활성화 |
| created-since | 필수, 기본값 없음 | 최초 도입 이후 이미지에만 자동 분석 적용 |
| concurrency | 2 | 동시 외부 호출 작업 수 |
| queue-capacity | 20 | 실행기 메모리 대기열 상한 |
| batch-size | 20 | 한 배치에서 등록·제출할 작업 수 |
| max-attempts | 3 | 최초 실행을 포함한 최대 시도 횟수 |
| request-timeout | 30s | 각 설명/임베딩 호출 제한 시간 계약 |
| retry-delay | 1m | 실패 후 재시도 대기 시간 |
| stuck-after | 3m | 중단된 PROCESSING 작업 회수 기준 |
| image-url-ttl | 5m | 이미지 GET 서명 URL 유효기간 |
| sweep-delay | 60000 (밀리초) | 배치 최초 대기 및 실행 간격 |

도입 시각은 재시작할 때마다 현재 시각으로 바꾸지 않고 고정한다. 그래야 이전 실행에서
등록하지 못했던 이미지를 재시작 후에도 찾을 수 있다. 배치 크기는 분석 작업 처리량 제한이며
최종 검색 결과 개수 제한과 관계없다.

외부 호출 타임아웃은 현재 인터페이스 계약이다. 실제 HTTP 시간 제한은 후속 제공자 어댑터에서
구현·검증한다. 복구 시간은 두 AI 호출의 제한 시간 합보다 길게 설정한다.

## 커밋 이후 처리와 독립 트랜잭션

원본 이미지 변경이 확정된 뒤 분석을 등록해야 롤백된 업로드를 분석하지 않는다.

- AFTER_COMMIT 리스너는 커밋 성공 시 호출된다. 트랜잭션 밖에서 발행한 이벤트는 기본적으로 처리하지 않는다.
- 등록은 별도 빈의 REQUIRES_NEW 메서드에 맡긴다. 독립 트랜잭션으로 분석 대기 작업을 커밋한다.
- 등록 실패는 기존 업로드를 실패 응답으로 바꾸지 않고 복구 배치에서 재시도한다.
- REQUIRES_NEW는 별도 커넥션을 사용할 수 있으므로 커넥션 풀 크기를 고려해야 한다.
- 근거: [트랜잭션 이벤트](https://docs.spring.io/spring-framework/reference/6.2/data-access/transaction/event.html),
  [트랜잭션 전파](https://docs.spring.io/spring-framework/reference/6.2/data-access/transaction/declarative/tx-propagation.html).

## DB 작업 기록과 실행기 대기열

실행기 큐는 서버 종료 시 사라질 수 있으므로 DB에 기록한 작업 상태를 복구 기준으로 사용한다.

- 분석 문서를 먼저 저장하고 실행기에 제출한다. 큐에서 기다리는 동안은 PENDING이다.
- 실행을 시작한 워커가 조건부 갱신으로 PROCESSING 상태를 선점한다.
- 큐가 가득 차서 제출이 거절되어도 시도 횟수를 늘리지 않고 다음 배치에서 다시 제출한다.
- 전용 풀의 동시 실행 수와 큐 크기에 상한을 두어 AI 호출이 기존 썸네일 작업을 밀어내지 않게 한다.
- 같은 작업이 여러 번 제출될 수 있지만 한 번만 선점된다. 다만 중단 복구 시 외부 AI 호출이
  중복 발생할 가능성까지 없애는 것은 아니며, DB 결과 저장은 시도 번호로 보호한다.
- 근거: [Spring 작업 실행과 스케줄링](https://docs.spring.io/spring-framework/reference/6.2/integration/scheduling.html).

## 출력 포트로 외부 AI 의존성 분리

출력 포트는 애플리케이션이 외부 서비스에 요구하는 호출 계약을 나타낸다.

- 설명 생성 포트는 이미지 URL을 받아 설명·특징·모델·프롬프트 버전을 반환한다.
- 임베딩 포트는 텍스트를 받아 벡터와 모델 이름을 반환한다. 이후 검색어에도 같은 구현체를 사용한다.
- 워커는 제공자별 HTTP 응답 구조를 알 필요 없이 분석 순서와 실패 처리를 담당한다.
- 현재 테스트는 외부 호출만 대체하고 실제 JPA·PostgreSQL 저장과 이벤트 트랜잭션을 검증한다.

## OpenAI 모델 선정 이유

초기 구현의 단순성과 충분히 낮은 호출 비용을 우선하여 GPT-6 Luna와 text-embedding-3-small을 선택했다.

- 이미지의 장면·객체·색상을 짧게 추출하는 작업이므로 GPT-6 Luna의 추론을 끈다.
- 설명 생성과 임베딩을 같은 제공자로 구성해 계정·인증·외부 API 운영을 단순화한다.
- 최고 성능이나 최저 비용으로 검증된 선택은 아니다. 실제 검색 품질·지연·비용은 샘플로 비교한다.
- 호출은 출력 포트로 분리하여 다른 제공자나 모델로 교체할 수 있도록 유지한다.
- 근거: [GPT-6 Luna](https://developers.openai.com/api/docs/models/gpt-6-luna),
  [text-embedding-3-small](https://developers.openai.com/api/docs/models/text-embedding-3-small).

## OpenAI HTTP 어댑터

Spring RestClient로 설명 생성과 임베딩을 호출한다. SDK 의존성은 추가하지 않는다.

- 설명은 `/v1/responses`에 이미지 URL을 보내며 reasoning effort `none`, 이미지 detail `low`,
  출력 상한 384토큰, `store=false`를 사용한다. 기존 워커는 프리뷰 URL을 우선 제공한다.
- description 한 문장과 features 배열을 JSON 스키마로 지정한다. 완료되지 않은 응답·거절·잘못된
  JSON·빈 설명/특징·길이 초과는 정상 분석 결과로 저장하지 않는다.
- `/v1/embeddings`에는 설명 텍스트 또는 검색어를 전달하며 모델·차원·float 인코딩을 명시한다.
- 기본 임베딩은 text-embedding-3-small의 1536차원이다. 응답 모델·차원·인덱스·숫자·영벡터를 검사한다.
- HTTP 응답 대기에는 포트로 전달된 타임아웃을 실제 적용한다. 연결 제한은 5초다.
- HTTP 어댑터 자체는 자동 재시도하지 않는다. 업로드 분석은 DB의 시도 횟수·대기 시각으로 재시도하고,
  검색 요청의 장애는 503으로 반환한다. 서로 다른 계층의 재시도로 비용이 중첩되는 것을 피한다.
- 제공자 오류 본문·서명 URL·API 키를 예외 메시지나 로그에 기록하지 않는다. 설정 객체의 toString도 키를 가린다.
- 근거: [구조화된 출력](https://developers.openai.com/api/docs/guides/structured-outputs),
  [임베딩 요청](https://developers.openai.com/api/reference/resources/embeddings/methods/create).

## JSON 스키마가 보장하는 것과 검증할 것

구조화된 출력은 응답 필드와 타입을 지정하는 기능이며 사진 설명의 사실성을 보장하지 않는다.

- JSON 모양만 요청하는 것과 달리 필수 필드·타입·배열 개수·추가 필드 허용 여부를 지정한다.
- 거절이나 토큰 상한에 따른 응답 잘림은 별도 실패로 처리한다.
- 서버에서도 응답을 검증하여 비정상 데이터가 DB에 저장되지 않게 한다.
- 모델이 사진에서 무엇을 놓치거나 잘못 설명하는지는 실제 샘플 평가로 확인한다.

## 활성화 전 준비

API 어댑터 구현과 로컬 HTTP 테스트를 완료했지만 실제 OpenAI 호출은 아직 검증하지 않았다.

1. OpenAI 계정과 API 키를 준비하고 로컬 또는 서버의 비공개 환경 변수에 `OPENAI_API_KEY`를 지정한다.
2. RDS에 vector 확장이 활성화되어 있는지 확인한다.
3. `MEDIA_SEARCH_CREATED_SINCE`에 최초 도입 시각(UTC ISO-8601)을 고정한다.
4. 실제 사진·검색어 샘플로 임계값을 평가하고 `MEDIA_SEARCH_QUERY_MIN_SIMILARITY`를 지정한다.
5. 준비가 끝나면 `IMAGE_SEARCH_ENABLED=true`로 설정한다. 기본값은 false다.

개발·운영 Compose는 위 환경 변수를 컨테이너에 전달한다. 예시 env 파일에는 빈 값만 기록한다.
기능 활성화 시 API 키가 없으면 기동 시 설정 오류를 알린다. API 키를 저장소나 채팅에 붙이지 않는다.
모델·차원·출력 상한은 `media.search.openai` 설정으로 관리한다. 임베딩 모델이나 차원을 바꾸면
기존 벡터와 검색 좌표가 달라질 수 있으므로 재분석 계획을 함께 세워야 한다.

## HTTP 도구 선택과 가독성 정리

코드를 읽을 때는 다음 흐름을 따라간다.

| 흐름 | 진입점 | 책임 |
| --- | --- | --- |
| 업로드 후 분석 | `ImageAnalysisTrigger` | 업로드 커밋 후 검색 문서 등록과 비동기 제출 |
| 분석 작업 | `AnalyzeImageService` | 작업 선점 → 설명 생성 → 임베딩 → 저장 또는 실패 기록 |
| 복구 | `ImageAnalysisSweeper` | 누락 문서 등록 → 중단 작업 복구 → 대기 작업 제출 |
| 텍스트 검색 | `SearchImagesService` | 검색어 정리 → 사용 가능 여부 확인 → 임베딩 → 결과 조회 |
| 결과 조립 | `ImageSearchResultAssembler` | 미디어 배치 조회와 유사도 순서 복원 |
| AI HTTP 호출 | `OpenAiSearchClient` | 인증·타임아웃·응답 수신과 안전한 오류 변환 |

필드 이름은 `documentRepository`, `fileRepository`, `imageDescriptionPort`,
`textEmbeddingPort`, `resultAssembler`처럼 대상과 역할을 드러내도록 작성한다.
외부 호출과 DB 트랜잭션의 경계를 유지하고, 애플리케이션은 공통 AI 오류 타입을 사용한다.
OpenAI 어댑터는 제공자 오류를 이 타입으로 변환하므로 분석 서비스가 제공자 구현을 알 필요가 없다.

검색 Swagger는 기존 `미디어 조회` 태그에 포함한다. 요청 파라미터·정렬·임계값·빈 결과·오류 상태를
명시하고, `ImageSearchMatchResponse`에 미디어와 유사도의 필드 설명·예시를 제공한다.
`/v3/api-docs`를 조회하는 통합 테스트로 실제 생성되는 응답 스키마를 확인한다.

현재 두 개의 동기 API만 호출하므로 Spring RestClient를 유지한다. 공식 SDK도 유효한 대안이다.

| 선택지 | 적합한 상황 | 현재 판단 |
| --- | --- | --- |
| RestClient | Spring MVC에서 적은 수의 동기 REST API 호출 | 유지. 요청·검증·재시도 정책을 직접 관리 |
| OpenAI 공식 Java SDK | 타입 기반 요청·응답, 구조화된 출력과 OpenAI 기능 확대 | JSON 처리 부담을 줄일 수 있어 기능 확대 시 재검토 |
| WebClient | 많은 동시 요청·비동기 스트리밍 등 리액티브 처리 | 현재 워커 구조에서는 추가 복잡성의 이점이 작음 |

RestClient가 항상 최선이라고 단정하지 않는다. 수동 JSON 코드는 필드 오타·API 변경 대응 비용이 있다.
현재는 요청 생성·완료 상태 확인·JSON 해석·필드 검증을 별도 메서드로 분리했다.
이미지 요청은 ObjectNode로 필드별 작성하고 임베딩 요청은 명시적 record로 표현한다.
공식 SDK는 Java 클래스 기반 스키마 생성과 응답 변환을 지원하므로 OpenAI 전용 기능이 늘면
의존성·재시도·타임아웃 정책을 함께 검토하여 교체한다.

- 근거: [Spring REST 클라이언트](https://docs.spring.io/spring-framework/reference/6.2/integration/rest-clients.html),
  [OpenAI Java SDK](https://developers.openai.com/api/reference/java).
- #460 운영 코드와 관련 테스트는 지역 변수 타입을 명시한다.
- 분석 서비스는 선점·이미지 분석·결과 저장·실패 기록으로 나눈다.
- 검색 서비스는 검색어 정리·사용 가능 여부 검사·임베딩 생성/검증·결과 조회로 나눈다.
- 응답 조립은 배치 조회 결과를 미디어 ID로 연결하고 검색 유사도 순서를 명시적으로 복원한다.

## 사용자가 준비할 OpenAI 설정

애플리케이션용 프로젝트와 API 키를 준비하고 API 결제 및 비용 제어를 설정한다.

1. API Platform에서 `sssOK-dev` 프로젝트를 만들고 API 결제를 설정한다.
2. 프로젝트 API 키를 생성하고 실행 환경의 비공개 `OPENAI_API_KEY`에 지정한다.
3. GPT-6 Luna와 text-embedding-3-small을 사용할 수 있는지 프로젝트의 모델 접근/한도를 확인한다.
4. 소액 사용 알림을 설정한다. 자동 차단을 원하면 별도로 hard spend limit을 지정한다.
5. 키 설정이 완료되면 소량 사진으로 실제 호출과 초기 임계값을 확인한다.

알림은 비용 상한을 강제하지 않는다. hard spend limit은 해당 한도에 도달하면 관련 API 호출을
429로 거절한다. 기본 자동 분석 비활성화는 실제 샘플 평가와 도입 시각 준비 후 해제한다.
로컬 Spring 실행은 .env 파일을 자동으로 읽지 않으므로 IDE 실행 환경 변수나 터미널 환경에
키를 지정한다. 개발·운영 Compose는 예시 env 파일에 정리한 변수들을 컨테이너로 전달한다.

근거: [OpenAI 시작 안내](https://developers.openai.com/api/docs/quickstart),
[비용 제한](https://developers.openai.com/api/docs/guides/spend-limits).
