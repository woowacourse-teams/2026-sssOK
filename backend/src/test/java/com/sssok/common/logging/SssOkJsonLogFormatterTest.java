package com.sssok.common.logging;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.env.MockEnvironment;

class SssOkJsonLogFormatterTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final LoggerContext loggerContext = new LoggerContext();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    @DisplayName("공통 필드를 모두 담아 한 줄 JSON 으로 낸다")
    void writesCommonFields() throws Exception {
        JsonNode log = format(formatter(), event(Level.INFO, "방을 만들었습니다"));

        assertThat(log.get(LogFields.TIMESTAMP).asText()).isNotBlank();
        assertThat(log.get(LogFields.LEVEL).asText()).isEqualTo("INFO");
        assertThat(log.get(LogFields.SERVICE).asText()).isEqualTo("sssok-backend");
        assertThat(log.get(LogFields.ENVIRONMENT).asText()).isEqualTo("prod");
        assertThat(log.get(LogFields.MESSAGE).asText()).isEqualTo("방을 만들었습니다");
        assertThat(log.get(LogFields.RELEASE_VERSION).asText()).isEqualTo("1.4.0");
        assertThat(log.get(LogFields.GIT_SHA).asText()).isEqualTo("abc1234");
    }

    @Test
    @DisplayName("한 줄로 끝난다 — 수집기가 줄 단위로 읽는다")
    void writesSingleLine() {
        String raw = formatter().format(event(Level.INFO, "여러\n줄\n메시지"));

        assertThat(raw).endsWith("\n");
        assertThat(raw.stripTrailing()).doesNotContain("\n");
    }

    @Test
    @DisplayName("요청 컨텍스트가 MDC 에 있으면 필드로 싣는다")
    void writesRequestContext() throws Exception {
        MDC.put(LogFields.REQUEST_ID, "0b5f1c2e-9a3d-4b7e-8c1a-2d3e4f5a6b7c");
        MDC.put(LogFields.METHOD, "POST");
        MDC.put(LogFields.PATH, "/api/v1/rooms/1/media");
        MDC.put(LogFields.STATUS, "500");
        MDC.put(LogFields.ERROR_CODE, "INTERNAL_SERVER_ERROR");

        JsonNode log = format(formatter(), event(Level.ERROR, "실패"));

        assertThat(log.get(LogFields.REQUEST_ID).asText()).isEqualTo("0b5f1c2e-9a3d-4b7e-8c1a-2d3e4f5a6b7c");
        assertThat(log.get(LogFields.METHOD).asText()).isEqualTo("POST");
        assertThat(log.get(LogFields.PATH).asText()).isEqualTo("/api/v1/rooms/1/media");
        assertThat(log.get(LogFields.ERROR_CODE).asText()).isEqualTo("INTERNAL_SERVER_ERROR");
    }

    @Test
    @DisplayName("status 는 숫자로 낸다 — Grafana 에서 크기 비교를 할 수 있어야 한다")
    void writesStatusAsNumber() throws Exception {
        MDC.put(LogFields.STATUS, "500");

        JsonNode status = format(formatter(), event(Level.ERROR, "실패")).get(LogFields.STATUS);

        assertThat(status.isNumber()).isTrue();
        assertThat(status.asInt()).isEqualTo(500);
    }

    @Test
    @DisplayName("durationMs 도 숫자로 낸다 — 느린 요청을 임계값으로 걸러야 한다")
    void writesDurationAsNumber() throws Exception {
        MDC.put(LogFields.DURATION_MS, "1234");

        JsonNode duration = format(formatter(), event(Level.INFO, "GET /api/v1/rooms 200"))
            .get(LogFields.DURATION_MS);

        assertThat(duration.isNumber()).isTrue();
        assertThat(duration.asInt()).isEqualTo(1234);
    }

    @Test
    @DisplayName("요청 밖에서 남긴 로그에는 요청 필드를 넣지 않는다")
    void omitsRequestContextOutsideRequest() throws Exception {
        JsonNode log = format(formatter(), event(Level.INFO, "만료된 방 12건을 정리했습니다"));

        assertThat(log.has(LogFields.REQUEST_ID)).isFalse();
        assertThat(log.has(LogFields.METHOD)).isFalse();
        assertThat(log.has(LogFields.STATUS)).isFalse();
        assertThat(log.has(LogFields.ERROR_CODE)).isFalse();
        assertThat(log.has(LogFields.DURATION_MS)).isFalse();
    }

    @Test
    @DisplayName("메시지·MDC·스택트레이스의 민감한 값을 가린다")
    void masksSensitiveValues() throws Exception {
        MDC.put(LogFields.PATH, "/api/v1/files?X-Amz-Signature=deadbeef");
        LoggingEvent event = event(Level.ERROR, "Bearer aaa.bbb.ccc 검증 실패");
        event.setThrowableProxy(new ch.qos.logback.classic.spi.ThrowableProxy(
            new IllegalStateException("password=hunter2")));

        String raw = formatter().format(event);

        assertThat(raw).doesNotContain("deadbeef");
        assertThat(raw).doesNotContain("aaa.bbb.ccc");
        assertThat(raw).doesNotContain("hunter2");
    }

    @Test
    @DisplayName("예외가 있으면 클래스 이름과 스택트레이스를 함께 싣는다")
    void writesThrowable() throws Exception {
        LoggingEvent event = event(Level.ERROR, "처리되지 않은 예외가 발생했습니다");
        event.setThrowableProxy(new ch.qos.logback.classic.spi.ThrowableProxy(
            new IllegalStateException("터짐")));

        JsonNode log = format(formatter(), event);

        assertThat(log.get("exception").asText()).isEqualTo(IllegalStateException.class.getName());
        assertThat(log.get("stackTrace").asText()).contains("IllegalStateException");
    }

    private SssOkJsonLogFormatter formatter() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        environment.setProperty("log.service", "sssok-backend");
        environment.setProperty("release.version", "1.4.0");
        environment.setProperty("release.git-sha", "abc1234");
        environment.setProperty("release.backend-version", "2.0.0");
        return new SssOkJsonLogFormatter(environment);
    }

    private JsonNode format(SssOkJsonLogFormatter formatter, ILoggingEvent event) throws Exception {
        return MAPPER.readTree(formatter.format(event));
    }

    private LoggingEvent event(Level level, String message) {
        LoggingEvent event = new LoggingEvent(
            getClass().getName(), loggerContext.getLogger("com.sssok.Test"), level, message, null, null);
        // 직접 만든 LoggerContext 에는 MDC 어댑터가 없어, 맵을 비워 두면 logback 이 뒤늦게
        // 어댑터를 찾다 NPE 를 낸다. 비어 있어도 빈 맵을 명시적으로 넣는다.
        Map<String, String> mdc = MDC.getCopyOfContextMap();
        event.setMDCPropertyMap(mdc == null ? Map.of() : mdc);
        return event;
    }
}
