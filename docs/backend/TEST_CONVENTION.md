# 백엔드 테스트 컨벤션

계층마다 검증 목적이 다르므로, 테스트 종류와 DB를 계층별로 다르게 가져간다.

## 요약

| 계층 | 테스트 종류 | DB | 주요 도구 |
| --- | --- | --- | --- |
| Repository + Service | 통합 테스트 | 기본 H2, PostgreSQL 전용 기능만 Testcontainers | `@DataJpaTest`/`@SpringBootTest` + JUnit |
| Controller | 슬라이스 테스트 | 없음(Mock) | `@WebMvcTest` + `MockMvc` |
| API 인수 테스트 | 통합 테스트 | Testcontainers(PostgreSQL) | `@SpringBootTest` + `MockMvc` |

## 1. Repository + Service — 통합 테스트

Repository와 Service를 계층별로 쪼개 각각 단위 테스트하지 않고, **하나의 통합 테스트**로 같이 검증한다. Service가 Repository를 실제로 호출했을 때의 동작(저장·조회 결과)까지 한 번에 확인하는 게 목적이라, Mock으로 Repository를 대체하지 않는다.

- **기본 CRUD·일반 로직**은 H2로 검증한다. 실행이 빠르고 대부분의 케이스는 어떤 RDB를 쓰든 동작이 같다.
- **해당 DB에 종속적인 내용**(PostgreSQL 전용 타입·함수, JSONB, 네이티브 쿼리 등)은 H2로 검증할 수 없으므로 Testcontainers로 실제 PostgreSQL을 띄워 검증한다.
- 하나의 기능 안에서도 두 그룹이 섞일 수 있다 — 일반 CRUD 부분은 H2 테스트로, PostgreSQL 전용 기능을 쓰는 부분만 별도로 Testcontainers 테스트를 추가한다.

예시(이 저장소 기준):
- `RoomRepository.save`/`findByCode`, `CreateRoomService`/`GetRoomService`의 흐름 검증 → H2
- 이후 JSONB 컬럼이나 PostgreSQL 전용 인덱스/쿼리를 쓰는 기능이 생기면, 그 부분만 Testcontainers로 별도 검증

## 2. Controller — 슬라이스 테스트

`@WebMvcTest`로 웹 계층만 띄우는 슬라이스 테스트를 작성한다. Service는 `@MockBean`으로 대체하고, `MockMvc`로 다음만 확인한다.

- 요청 매핑(`@PathVariable`, `@RequestBody` 등)이 올바른지
- 응답 형식(`ApiResponse`, HTTP 상태 코드)이 올바른지
- 유효성 검증 실패·예외 발생 시 `GlobalExceptionHandler`가 기대한 에러 응답으로 변환하는지

DB나 전체 Spring Context를 띄우지 않아 실행이 빠르다. Service 내부 로직 자체는 이 테스트의 관심사가 아니다(1번에서 이미 검증됨).

## 3. API 인수 테스트 — 통합 테스트

컨트롤러 → 서비스 → 리포지토리 → DB까지 전체 흐름이 실제로 맞물려 동작하는지 확인하는 테스트다. `@SpringBootTest` + `@AutoConfigureMockMvc`로 애플리케이션 컨텍스트를 통째로 띄우고, Testcontainers로 실제 PostgreSQL을 연결한 뒤 `MockMvc`로 실제 HTTP 요청처럼 호출한다.

- Mock을 쓰지 않는다 — 실제 DB, 실제 Flyway 마이그레이션, 실제 JPA 매핑까지 전부 맞물려야 통과한다.
- `spring.jpa.hibernate.ddl-auto=validate`로 고정해서, Flyway가 만든 스키마와 엔티티 매핑이 실제로 일치하는지도 함께 검증한다.
- 이미 이 저장소의 `RoomApiTest`, `AuthApiTest`가 이 계층에 해당한다.

## 현재 적용 현황

- **Auth(익명 인증) 기능**은 3단계 구조를 모두 적용했다: `AnonymousAuthServiceTest`(Repository+Service, H2) / `AuthControllerTest`(Controller 슬라이스) / `AuthApiTest`(API 인수 테스트, Testcontainers)
- **Room 기능**도 3단계 구조를 모두 적용했다: `UpdateRoomServiceTest`·`DeleteRoomServiceTest`(Repository+Service, H2) / `RoomControllerTest`(Controller 슬라이스) / `RoomApiTest`(API 인수 테스트, Testcontainers)
- **입장(`JoinRoomServiceTest`·`JoinRoomConcurrencyTest`)만 예외적으로 Testcontainers를 쓴다** — 입장 멱등성을 `ON CONFLICT DO NOTHING`(PostgreSQL 전용 네이티브 쿼리)으로 보장하므로 H2로는 검증할 수 없다. 위 "1. Repository + Service" 절의 *DB 종속 기능은 Testcontainers* 규칙을 그대로 적용한 사례다.
- **Folder 기능**은 아직 도메인 단위 테스트만 있다 — 서비스·API가 생기면 이 구조로 맞춰 나간다
- H2 의존성(`com.h2database:h2`)은 `backend/build.gradle`에 추가되어 있다

## H2 통합 테스트 작성 시 참고

Flyway 마이그레이션은 PostgreSQL 전용 문법(`BIGSERIAL`, `TIMESTAMPTZ` 등)을 쓰므로 H2에서 그대로 실행되지 않는다. Repository+Service 테스트는 Flyway를 끄고 Hibernate가 엔티티로부터 스키마를 직접 생성하도록 한다 (실제 마이그레이션-엔티티 정합성 검증은 API 인수 테스트의 `ddl-auto=validate`가 담당하므로 역할이 겹치지 않는다).

H2 설정(데이터소스, `ddl-auto`, Flyway 끄기, 테스트 전용 `jwt.secret`)은 테스트 클래스마다 반복해서 적지 않고, `backend/src/test/resources/application-test.yml`(`test` 프로파일) 하나에 모아뒀다. 새 Repository+Service 테스트를 추가할 때는 아래처럼 프로파일만 지정하면 된다.

```java
@SpringBootTest
@ActiveProfiles("test")
class SomeServiceTest {
    // ...
}
```
