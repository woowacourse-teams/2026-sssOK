package com.sssok.common.logging;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.MockAsyncContext;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestLoggingFilterTest {

    // 수집기·배포 헬스체크가 반복 호출하는 경로. 필터의 제외 목록이 좁아지면 여기서 먼저 깨진다.
    private static final List<String> EXCLUDED_PATHS = List.of(
        "/health",
        "/actuator",
        "/actuator/health",
        "/actuator/health/liveness",
        "/actuator/health/readiness",
        "/actuator/info",
        "/actuator/prometheus");

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
    @DisplayName("반복 호출되는 헬스체크와 Actuator 수집 요청은 접근 로그에서 제외한다")
    void skipsMonitoringAccessLogs() throws Exception {
        List<ILoggingEvent> logged = captureAccessLogs(() -> {
            for (String path : EXCLUDED_PATHS) {
                filter.doFilter(get(path), new MockHttpServletResponse(), new MockFilterChain());
            }
        });

        assertThat(logged).isEmpty();
    }

    @Test
    @DisplayName("접근 로그에서 뺀 경로도 요청 ID 는 발급해 돌려준다 — 헬스체크가 실패하면 그 ID 로 찾는다")
    void issuesRequestIdForExcludedPaths() throws Exception {
        for (String path : EXCLUDED_PATHS) {
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(get(path), response, new MockFilterChain());

            assertThat(response.getHeader(RequestLoggingFilter.REQUEST_ID_HEADER))
                .as("%s 의 응답 요청 ID", path)
                .isNotNull();
        }
    }

    @Test
    @DisplayName("접근 로그에서 뺀 경로라도 5xx 는 남긴다 — 헬스체크가 깨진 순간이 가장 보고 싶은 줄이다")
    void logsServerErrorOnExcludedPaths() throws Exception {
        List<ILoggingEvent> logged = captureAccessLogs(() -> {
            for (String path : EXCLUDED_PATHS) {
                filter.doFilter(get(path), new MockHttpServletResponse(), respondWith(503));
            }
        });

        assertThat(logged)
            .hasSize(EXCLUDED_PATHS.size())
            .allSatisfy(event -> assertThat(event.getFormattedMessage()).contains("503"));
    }

    @Test
    @DisplayName("4xx 는 제외 경로에서 계속 묻는다 — 헬스체크 경로 오타 하나로 로그가 쌓이면 안 된다")
    void stillSkipsClientErrorOnExcludedPaths() throws Exception {
        List<ILoggingEvent> logged = captureAccessLogs(
            () -> filter.doFilter(get("/actuator/prometheus"), new MockHttpServletResponse(), respondWith(404)));

        assertThat(logged).isEmpty();
    }

    @Test
    @DisplayName("접근 로그에는 처리 시간이 durationMs 필드로 함께 실린다")
    void putsDurationIntoAccessLog() throws Exception {
        List<ILoggingEvent> logged = captureAccessLogs(
            () -> filter.doFilter(get("/api/v1/rooms"), new MockHttpServletResponse(), new MockFilterChain()));

        assertThat(logged).singleElement()
            .satisfies(event -> assertThat(event.getMDCPropertyMap().get(LogFields.DURATION_MS))
                .isNotNull()
                .matches("\\d+"));
    }

    @Test
    @DisplayName("비동기로 넘어간 요청도 시작 줄을 남긴다 — SSE 연결이 로그에서 사라지면 안 된다")
    void logsAsyncStart() throws Exception {
        Logger filterLogger = (Logger) LoggerFactory.getLogger(RequestLoggingFilter.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        filterLogger.addAppender(appender);

        MockHttpServletRequest request = get("/api/v1/rooms/1/events");
        request.setAsyncSupported(true);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> ((MockHttpServletRequest) req).startAsync());

        filterLogger.detachAppender(appender);
        assertThat(appender.list)
            .extracting(ILoggingEvent::getFormattedMessage)
            .anyMatch(message -> message.contains("비동기 응답 시작"));
        // 최종 상태가 아직 안 정해졌으므로 완료형 접근 로그는 남기지 않는다.
        assertThat(appender.list)
            .extracting(ILoggingEvent::getFormattedMessage)
            .noneMatch(message -> message.contains("200 ("));
    }

    @Test
    @DisplayName("비동기 요청이 정상 종료되면 종료 줄에 최종 status 가 실린다")
    void putsFinalStatusIntoAsyncCompleteLog() throws Exception {
        MdcSnapshot snapshot = runAsyncAndComplete(200, listener -> listener.onComplete(null));

        assertThat(snapshot.forMessage("비동기 응답 종료")).containsEntry(LogFields.STATUS, "200");
    }

    @Test
    @DisplayName("비동기 오류로 끝나면 종료 줄의 status 로 실패를 검색할 수 있다")
    void putsFinalStatusIntoAsyncErrorLog() throws Exception {
        MdcSnapshot snapshot = runAsyncAndComplete(500,
            listener -> listener.onError(asyncEvent(new IllegalStateException("업스트림 끊김"))));

        assertThat(snapshot.forMessage("비동기 응답 오류")).containsEntry(LogFields.STATUS, "500");
    }

    @Test
    @DisplayName("비동기 타임아웃도 status 와 함께 남는다")
    void putsFinalStatusIntoAsyncTimeoutLog() throws Exception {
        MdcSnapshot snapshot = runAsyncAndComplete(503, listener -> listener.onTimeout(null));

        assertThat(snapshot.forMessage("비동기 응답 타임아웃")).containsEntry(LogFields.STATUS, "503");
    }

    @Test
    @DisplayName("연결 종료가 아닌 IOException 은 WARN 으로 남긴다 — 서버 I/O 장애가 묻히면 안 된다")
    void logsUnknownIoExceptionAsWarn() throws Exception {
        MdcSnapshot snapshot = runAsyncAndComplete(500,
            listener -> listener.onError(asyncEvent(new IOException("No space left on device"))));

        assertThat(snapshot.levelOfMessage("비동기 응답 오류")).isEqualTo(Level.WARN);
    }

    @Test
    @DisplayName("Broken pipe 는 클라이언트 연결 종료로 보고 DEBUG 로 내린다")
    void logsBrokenPipeAsDebug() throws Exception {
        MdcSnapshot snapshot = runAsyncAndComplete(200,
            listener -> listener.onError(asyncEvent(new IOException("Broken pipe"))));

        assertThat(snapshot.levelOfMessage("클라이언트가 연결을 끊었습니다")).isEqualTo(Level.DEBUG);
    }

    @Test
    @DisplayName("원인 예외가 Connection reset 이어도 연결 종료로 본다")
    void logsWrappedConnectionResetAsDebug() throws Exception {
        MdcSnapshot snapshot = runAsyncAndComplete(200, listener -> listener.onError(
            asyncEvent(new IllegalStateException("래핑", new IOException("Connection reset by peer")))));

        assertThat(snapshot.levelOfMessage("클라이언트가 연결을 끊었습니다")).isEqualTo(Level.DEBUG);
    }

    // 비동기로 넘어간 요청을 만들고, 등록된 리스너를 원하는 종료 시나리오로 직접 깨운다.
    // 종료 시점의 응답 상태를 status 로 읽는지 보려면 완료 직전에 상태를 바꿔 둬야 한다.
    private MdcSnapshot runAsyncAndComplete(int finalStatus, AsyncEnding ending) throws Exception {
        Logger filterLogger = (Logger) LoggerFactory.getLogger(RequestLoggingFilter.class);
        Level originalLevel = filterLogger.getLevel();
        filterLogger.setLevel(Level.DEBUG);
        MdcSnapshot snapshot = new MdcSnapshot();
        filterLogger.addAppender(snapshot.appender());

        MockHttpServletRequest request = get("/api/v1/rooms/1/events");
        request.setAsyncSupported(true);
        MockHttpServletResponse response = new MockHttpServletResponse();

        try {
            filter.doFilter(request, response, (req, res) -> ((MockHttpServletRequest) req).startAsync());
            response.setStatus(finalStatus);
            ending.end(listenerOf(request));
        } finally {
            filterLogger.detachAppender(snapshot.appender());
            filterLogger.setLevel(originalLevel);
        }
        return snapshot;
    }

    private AsyncListener listenerOf(MockHttpServletRequest request) {
        List<AsyncListener> listeners = ((MockAsyncContext) request.getAsyncContext()).getListeners();
        assertThat(listeners).hasSize(1);
        return listeners.get(0);
    }

    private AsyncEvent asyncEvent(Throwable error) {
        return new AsyncEvent(null, error);
    }

    // AsyncListener 의 콜백은 IOException 을 던질 수 있어 Consumer 로는 못 받는다.
    @FunctionalInterface
    private interface AsyncEnding {

        void end(AsyncListener listener) throws IOException;
    }

    private MockHttpServletRequest get(String path) {
        return new MockHttpServletRequest("GET", path);
    }

    // 필터가 남긴 로그만 모은다. 접근 로그를 "남겼는지" 자체가 검증 대상이라 실제 appender 로 본다.
    private List<ILoggingEvent> captureAccessLogs(ThrowingRunnable requests) throws Exception {
        Logger filterLogger = (Logger) LoggerFactory.getLogger(RequestLoggingFilter.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        filterLogger.addAppender(appender);
        try {
            requests.run();
        } finally {
            filterLogger.detachAppender(appender);
            appender.stop();
        }
        return List.copyOf(appender.list);
    }

    private FilterChain respondWith(int status) {
        return (request, response) -> ((HttpServletResponse) response).setStatus(status);
    }

    @FunctionalInterface
    private interface ThrowingRunnable {

        void run() throws Exception;
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

    // 로그 한 줄이 어떤 레벨로, 어떤 MDC 를 달고 나갔는지 본다. 비동기 완료 콜백은 MDC 를 스스로
    // 채웠다가 지우므로, 기록 시점에 붙어 있던 값은 이벤트에 남은 사본으로만 확인할 수 있다.
    private static final class MdcSnapshot {

        private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

        private MdcSnapshot() {
            appender.start();
        }

        private ListAppender<ILoggingEvent> appender() {
            return appender;
        }

        private Map<String, String> forMessage(String fragment) {
            return eventOf(fragment).getMDCPropertyMap();
        }

        private Level levelOfMessage(String fragment) {
            return eventOf(fragment).getLevel();
        }

        private ILoggingEvent eventOf(String fragment) {
            return appender.list.stream()
                .filter(event -> event.getFormattedMessage().contains(fragment))
                .findFirst()
                .orElseThrow(() -> new AssertionError(fragment + " 로그가 남지 않았습니다: " + appender.list));
        }
    }
}
