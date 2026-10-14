package com.sssok.common.logging;

import static org.assertj.core.api.Assertions.assertThat;

import com.sssok.infrastructure.config.VideoWorkerConfig;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * MDC 가 요청 스레드 밖으로 새지 않는지 본다.
 *
 * <p>이 앱은 비동기 실행기에 {@code TaskDecorator} 를 걸지 않기로 했다 (이유는
 * {@link com.sssok.infrastructure.config.VideoWorkerConfig} 주석). 결정만 적어 두면 나중에
 * "워커 로그에도 requestId 가 있으면 편하겠다" 며 데코레이터 한 줄이 들어와도 아무 테스트도 깨지지
 * 않는다. 그러면 요청 ID 하나로 요청 하나를 집어내는 추적이 조용히 흐려진다.
 *
 * <p>스레드 풀은 워커를 하나만 둔다. 연달아 던진 두 작업이 반드시 같은 스레드에서 돌아야
 * "재사용된 스레드에 앞 값이 남는가" 를 확인할 수 있기 때문이다.
 */
class MdcIsolationTest {

    private static final String REQUEST_ID_FIELD = LogFields.REQUEST_ID;

    // MDC 는 스레드마다 따로라, 작업이 실제로 본 값은 그 스레드 안에서 찍어 건네받아야 한다.
    // 어느 스레드에서 돌았는지도 같은 지도에 함께 담아 되돌린다.
    private static final String WORKER_THREAD = "workerThread";

    private final RequestLoggingFilter filter = new RequestLoggingFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    @DisplayName("같은 스레드로 요청 두 건을 연달아 처리해도 앞 요청의 MDC 값이 뒤 요청에 남지 않는다")
    void doesNotLeakMdcBetweenRequestsOnSameThread() throws Exception {
        String firstId = "11111111-1111-4111-8111-111111111111";
        MockHttpServletRequest first = get("/api/v1/rooms");
        first.addHeader(RequestLoggingFilter.REQUEST_ID_HEADER, firstId);
        filter.doFilter(first, new MockHttpServletResponse(), new MockFilterChain());

        // 두 번째 요청은 ID 를 보내지 않아 서버가 새로 발급한다. 앞 요청 값이 남아 있으면 여기서 보인다.
        CapturingFilterChain second = new CapturingFilterChain();
        filter.doFilter(get("/api/v1/rooms/9/media"), new MockHttpServletResponse(), second);

        assertThat(second.captured.get(REQUEST_ID_FIELD)).isNotNull().isNotEqualTo(firstId);
        assertThat(second.captured)
            .containsEntry(LogFields.PATH, "/api/v1/rooms/9/media")
            .doesNotContainEntry(LogFields.PATH, "/api/v1/rooms");
    }

    @Test
    @DisplayName("요청 중 워커로 넘긴 작업의 MDC 에는 requestId 가 없다 — 요청 추적은 요청 스레드에서 끝난다")
    void doesNotPropagateRequestIdToWorker() throws Exception {
        ThreadPoolTaskExecutor executor = singleWorkerExecutor();
        try {
            Map<String, String> insideWorker = runDuringRequest(executor);

            assertThat(insideWorker).doesNotContainKey(REQUEST_ID_FIELD);
            // path·status 등 다른 요청 필드도 함께 넘어가면 안 된다.
            assertThat(insideWorker).containsOnlyKeys(WORKER_THREAD);
        } finally {
            executor.shutdown();
        }
    }

    @Test
    @DisplayName("같은 워커 스레드가 두 요청의 작업을 연달아 처리해도 앞 작업의 MDC 값이 남지 않는다")
    void doesNotLeakMdcBetweenTasksOnSameWorker() throws Exception {
        ThreadPoolTaskExecutor executor = singleWorkerExecutor();
        try {
            Map<String, String> firstTask = runDuringRequest(executor);
            Map<String, String> secondTask = runDuringRequest(executor);

            // 워커가 하나뿐이라 두 작업은 같은 스레드에서 돌았다. 그 전제가 깨지면 이 검증은 의미가 없다.
            assertThat(firstTask.get(WORKER_THREAD)).isEqualTo(secondTask.get(WORKER_THREAD));
            assertThat(secondTask).containsOnlyKeys(WORKER_THREAD);
        } finally {
            executor.shutdown();
        }
    }

    // 요청 스레드에서 MDC 가 차 있는 동안 워커로 작업 하나를 던지고, 그 작업이 본 MDC 를 돌려준다.
    private Map<String, String> runDuringRequest(ThreadPoolTaskExecutor executor) throws Exception {
        Map<String, String> seen = new HashMap<>();
        CountDownLatch done = new CountDownLatch(1);

        MockHttpServletRequest request = get("/api/v1/rooms");
        request.addHeader(RequestLoggingFilter.REQUEST_ID_HEADER, UUID.randomUUID().toString());

        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {
            // 요청 스레드에 requestId 가 차 있는 이 시점에 던진다 — 넘어가는지가 검증 대상이다.
            assertThat(MDC.get(REQUEST_ID_FIELD)).isNotNull();
            executor.execute(() -> {
                Map<String, String> snapshot = MDC.getCopyOfContextMap();
                if (snapshot != null) {
                    seen.putAll(snapshot);
                }
                seen.put(WORKER_THREAD, Thread.currentThread().getName());
                done.countDown();
            });
        });

        assertThat(done.await(5, TimeUnit.SECONDS)).as("워커 작업이 끝나지 않았습니다").isTrue();
        return seen;
    }

    // 프로덕션과 같은 팩터리로 만든다. 직접 조립하면 실제 빈에 TaskDecorator 가 붙는 날에도
    // 이 테스트는 통과해 버린다.
    private ThreadPoolTaskExecutor singleWorkerExecutor() {
        return (ThreadPoolTaskExecutor) new VideoWorkerConfig()
            .applicationTaskExecutor("mdc-isolation-worker-", 1, 1, 10);
    }

    private MockHttpServletRequest get(String path) {
        return new MockHttpServletRequest("GET", path);
    }

    // 체인 실행 시점의 MDC 를 찍어 둔다. 필터가 요청이 끝나며 지우므로 그 전에 봐야 한다.
    private static final class CapturingFilterChain extends MockFilterChain {

        private final Map<String, String> captured = new HashMap<>();

        @Override
        public void doFilter(jakarta.servlet.ServletRequest request, jakarta.servlet.ServletResponse response) {
            Map<String, String> snapshot = MDC.getCopyOfContextMap();
            if (snapshot != null) {
                captured.putAll(snapshot);
            }
        }
    }
}
