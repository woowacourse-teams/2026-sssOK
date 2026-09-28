package com.sssok.common.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.sssok.common.exception.ErrorCode;
import com.sssok.presentation.api.common.GlobalExceptionHandler;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 필터 → 컨트롤러 → 예외 처리기로 이어지는 동안 요청 ID 와 응답 정보가 실제로 로그에 실리는지 본다.
 * 조각별 단위 테스트가 다 통과해도 연결이 빠지면 운영에서 추적이 안 되므로 한 번은 이어서 확인한다.
 */
class RequestTraceIntegrationTest {

    private final Logger rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        appender.start();
        rootLogger.addAppender(appender);
        rootLogger.setLevel(Level.DEBUG);
        mockMvc = MockMvcBuilders.standaloneSetup(new TraceTestController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .addFilters(new RequestLoggingFilter())
            .build();
    }

    @AfterEach
    void tearDown() {
        rootLogger.detachAppender(appender);
        appender.stop();
    }

    @Test
    @DisplayName("500 응답 로그는 ERROR 레벨이고 status·errorCode·path 를 갖는다")
    void errorLogCarriesResponseContext() throws Exception {
        mockMvc.perform(get("/boom"));

        ILoggingEvent error = eventsOf(Level.ERROR).getFirst();
        Map<String, String> mdc = error.getMDCPropertyMap();
        assertThat(mdc).containsEntry(LogFields.STATUS, "500");
        assertThat(mdc).containsEntry(LogFields.ERROR_CODE, ErrorCode.INTERNAL_SERVER_ERROR.name());
        assertThat(mdc).containsEntry(LogFields.PATH, "/boom");
        assertThat(mdc.get(LogFields.REQUEST_ID)).isNotBlank();
    }

    @Test
    @DisplayName("한 요청에서 나온 모든 로그가 같은 requestId 를 갖는다")
    void allLogsShareOneRequestId() throws Exception {
        MvcResult result = mockMvc.perform(get("/boom")).andReturn();

        String issued = result.getResponse().getHeader(RequestLoggingFilter.REQUEST_ID_HEADER);
        List<String> requestIds = appender.list.stream()
            .map(event -> event.getMDCPropertyMap().get(LogFields.REQUEST_ID))
            .filter(java.util.Objects::nonNull)
            .distinct()
            .toList();

        assertThat(requestIds).containsExactly(issued);
    }

    @Test
    @DisplayName("Authorization 헤더를 보내도 토큰이 로그에 남지 않는다")
    void neverLogsAuthorizationHeader() throws Exception {
        String token = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiI0MiJ9.c2lnbmF0dXJl";

        mockMvc.perform(get("/boom").header(HttpHeaders.AUTHORIZATION, "Bearer " + token));

        assertThat(appender.list)
            .noneMatch(event -> event.getFormattedMessage().contains(token));
    }

    @Test
    @DisplayName("400 응답은 DEBUG 로 내린다 — 잘못 보낸 요청이 운영 로그를 채우면 안 된다")
    void clientErrorIsNotWarnOrHigher() throws Exception {
        mockMvc.perform(get("/bad-request"));

        assertThat(eventsOf(Level.WARN)).isEmpty();
        assertThat(eventsOf(Level.ERROR)).isEmpty();
        assertThat(eventsOf(Level.DEBUG)).isNotEmpty();
    }

    private List<ILoggingEvent> eventsOf(Level level) {
        return appender.list.stream().filter(event -> event.getLevel() == level).toList();
    }

    @RestController
    static class TraceTestController {

        @GetMapping("/boom")
        String boom() {
            throw new IllegalStateException("터짐");
        }

        @GetMapping("/bad-request")
        String badRequest() {
            throw new com.sssok.common.exception.InvalidRequestParameterException("roomId");
        }
    }
}
