package com.sssok.common.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestLoggingFilterTest {

    private final RequestLoggingFilter filter = new RequestLoggingFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    @DisplayName("요청 ID 가 없으면 서버가 UUID 를 발급해 응답 헤더로 돌려준다")
    void issuesRequestIdWhenAbsent() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(get("/api/v1/rooms"), response, new MockFilterChain());

        String issued = response.getHeader(RequestLoggingFilter.REQUEST_ID_HEADER);
        assertThat(issued).isNotNull();
        assertThat(UUID.fromString(issued)).hasToString(issued);
    }

    @Test
    @DisplayName("유효한 요청 ID 를 받으면 그대로 쓴다")
    void reusesValidRequestId() throws Exception {
        String given = "0b5f1c2e-9a3d-4b7e-8c1a-2d3e4f5a6b7c";
        MockHttpServletRequest request = get("/api/v1/rooms");
        request.addHeader(RequestLoggingFilter.REQUEST_ID_HEADER, given);
        MockHttpServletResponse response = new MockHttpServletResponse();
        CapturingFilterChain chain = new CapturingFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader(RequestLoggingFilter.REQUEST_ID_HEADER)).isEqualTo(given);
        assertThat(chain.captured).containsEntry(LogFields.REQUEST_ID, given);
    }

    @Test
    @DisplayName("형식에 맞지 않는 요청 ID 는 버리고 새로 발급한다")
    void replacesMalformedRequestId() throws Exception {
        MockHttpServletRequest request = get("/api/v1/rooms");
        // 대문자 UUID, 길이 초과, UUID 아님 — 셋 다 거절 대상이다.
        request.addHeader(RequestLoggingFilter.REQUEST_ID_HEADER, "NOT-A-UUID-" + "x".repeat(200));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        String issued = response.getHeader(RequestLoggingFilter.REQUEST_ID_HEADER);
        assertThat(issued).isNotNull().hasSize(36).doesNotContain("x");
    }

    @Test
    @DisplayName("대문자 UUID 도 거절한다 — 같은 요청이 두 값으로 검색되면 안 된다")
    void rejectsUppercaseUuid() throws Exception {
        MockHttpServletRequest request = get("/api/v1/rooms");
        String uppercase = UUID.randomUUID().toString().toUpperCase();
        request.addHeader(RequestLoggingFilter.REQUEST_ID_HEADER, uppercase);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader(RequestLoggingFilter.REQUEST_ID_HEADER)).isNotEqualTo(uppercase);
    }

    @Test
    @DisplayName("처리 중에는 method·path 가 MDC 에 차 있다")
    void putsRequestContextIntoMdc() throws Exception {
        CapturingFilterChain chain = new CapturingFilterChain();

        filter.doFilter(get("/api/v1/rooms/7/media"), new MockHttpServletResponse(), chain);

        assertThat(chain.captured)
            .containsEntry(LogFields.METHOD, "GET")
            .containsEntry(LogFields.PATH, "/api/v1/rooms/7/media");
    }

    @Test
    @DisplayName("요청이 끝나면 MDC 를 비워 다음 요청에 값이 새지 않는다")
    void clearsMdcAfterRequest() throws Exception {
        filter.doFilter(get("/api/v1/rooms"), new MockHttpServletResponse(), new MockFilterChain());

        assertThat(MDC.get(LogFields.REQUEST_ID)).isNull();
        assertThat(MDC.get(LogFields.METHOD)).isNull();
        assertThat(MDC.get(LogFields.PATH)).isNull();
        assertThat(MDC.get(LogFields.STATUS)).isNull();
        assertThat(MDC.get(LogFields.ERROR_CODE)).isNull();
    }

    @Test
    @DisplayName("체인에서 예외가 터져도 MDC 를 비우고 요청 ID 헤더는 남긴다")
    void clearsMdcEvenWhenChainThrows() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        try {
            filter.doFilter(get("/api/v1/rooms"), response, (req, res) -> {
                throw new IllegalStateException("터짐");
            });
        } catch (Exception expected) {
            // 예외는 그대로 위로 올라가야 GlobalExceptionHandler 가 받는다.
        }

        assertThat(response.getHeader(RequestLoggingFilter.REQUEST_ID_HEADER)).isNotNull();
        assertThat(MDC.get(LogFields.REQUEST_ID)).isNull();
    }

    @Test
    @DisplayName("비동기로 넘어간 요청도 시작 줄을 남긴다 — SSE 연결이 로그에서 사라지면 안 된다")
    void logsAsyncStart() throws Exception {
        ch.qos.logback.classic.Logger filterLogger =
            (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(RequestLoggingFilter.class);
        ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender =
            new ch.qos.logback.core.read.ListAppender<>();
        appender.start();
        filterLogger.addAppender(appender);

        MockHttpServletRequest request = get("/api/v1/rooms/1/events");
        request.setAsyncSupported(true);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> ((MockHttpServletRequest) req).startAsync());

        filterLogger.detachAppender(appender);
        assertThat(appender.list)
            .extracting(ch.qos.logback.classic.spi.ILoggingEvent::getFormattedMessage)
            .anyMatch(message -> message.contains("비동기 응답 시작"));
        // 최종 상태가 아직 안 정해졌으므로 완료형 접근 로그는 남기지 않는다.
        assertThat(appender.list)
            .extracting(ch.qos.logback.classic.spi.ILoggingEvent::getFormattedMessage)
            .noneMatch(message -> message.contains("200 ("));
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
