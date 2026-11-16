package com.sssok.infrastructure.monitoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.sun.net.httpserver.HttpServer;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import com.sssok.common.logging.RequestLoggingFilter;
import org.slf4j.LoggerFactory;
import org.junit.jupiter.api.BeforeAll;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@Tag("integration-h2")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "management.server.port=0", "management.tracing.enabled=true",
    "management.tracing.sampling.probability=1", "management.otlp.tracing.export.enabled=true"
})
@AutoConfigureObservability
@ActiveProfiles("test")
class HttpTracingTest {
    private static final CopyOnWriteArrayList<byte[]> PAYLOADS = new CopyOnWriteArrayList<>();
    private static volatile CountDownLatch collectorGate;
    private static volatile CountDownLatch collectorEntered;
    private static final HttpClient CLIENT = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(2)).build();
    private static final HttpServer COLLECTOR = collector();

    private static final CopyOnWriteArrayList<String> ACCESS_TRACE_IDS = new CopyOnWriteArrayList<>();
    private static final AppenderBase<ILoggingEvent> ACCESS_LOGS = new AppenderBase<>() {
        @Override
        protected void append(ILoggingEvent event) {
            String traceId = event.getMDCPropertyMap().get("traceId");
            if (traceId != null) ACCESS_TRACE_IDS.add(traceId);
        }
    };

    @BeforeAll
    static void captureAccessLogs() {
        ACCESS_LOGS.start();
        ((Logger) LoggerFactory.getLogger(RequestLoggingFilter.class)).addAppender(ACCESS_LOGS);
    }

    @LocalServerPort
    int port;

    @DynamicPropertySource
    static void endpoint(DynamicPropertyRegistry registry) {
        registry.add("management.otlp.tracing.endpoint", () ->
            "http://127.0.0.1:" + COLLECTOR.getAddress().getPort() + "/v1/traces");
    }

    private static HttpServer collector() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/v1/traces", exchange -> {
                byte[] body = exchange.getRequestBody().readAllBytes();
                PAYLOADS.add(body);
                CountDownLatch gate = collectorGate;
                try {
                    if (gate != null) {
                        collectorEntered.countDown();
                        gate.await(5, TimeUnit.SECONDS);
                    }
                    // 명시적으로 지정한 로컬 Tempo로만 실제 OTLP 전송을 재현한다.
                    String tempo = System.getenv("TEMPO_SMOKE_ENDPOINT");
                    if (tempo != null) {
                        var response = CLIENT.send(HttpRequest.newBuilder(URI.create(tempo + "/v1/traces"))
                            .timeout(Duration.ofSeconds(3)).header("Content-Type", "application/x-protobuf")
                            .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build(),
                            HttpResponse.BodyHandlers.discarding());
                        if (response.statusCode() != 200) throw new IOException("Tempo rejected spans");
                    }
                    exchange.sendResponseHeaders(200, -1);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    exchange.close();
                }
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void HTTP_요청을_OTLP로_내보내고_URL의_민감한_쿼리는_제외한다() throws Exception {
        String traceId = UUID.randomUUID().toString().replace("-", "");
        assertThat(request(traceId).statusCode()).isEqualTo(200);
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            var exported = PAYLOADS.stream()
                .filter(bytes -> HexFormat.of().formatHex(bytes).contains(traceId)).findFirst();
            assertThat(exported).isPresent();
            byte[] payload = exported.orElseThrow();
            assertThat(new String(payload, StandardCharsets.ISO_8859_1))
                .contains("/version", "sssok-backend", "deployment.environment")
                .doesNotContain("must-not-export");
        });
        assertThat(ACCESS_TRACE_IDS).contains(traceId);
        String queryEndpoint = System.getenv("TEMPO_SMOKE_QUERY_ENDPOINT");
        if (queryEndpoint != null) {
            await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
                var response = CLIENT.send(HttpRequest.newBuilder(
                    URI.create(queryEndpoint + "/api/traces/" + traceId)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
                assertThat(response.statusCode()).isEqualTo(200);
                assertThat(response.body()).contains("/version").doesNotContain("must-not-export");
            });
        }
    }

    @Test
    void 수집기가_응답하지_않아도_HTTP_요청은_완료된다() throws Exception {
        collectorEntered = new CountDownLatch(1);
        collectorGate = new CountDownLatch(1);
        try {
            assertThat(request(UUID.randomUUID().toString().replace("-", "")).statusCode()).isEqualTo(200);
            assertThat(collectorEntered.await(10, TimeUnit.SECONDS)).isTrue();
            // 내보내기 스레드가 기다리는 동안에도 새 요청은 자체 타임아웃 안에 완료되어야 한다.
            assertThat(request(UUID.randomUUID().toString().replace("-", "")).statusCode()).isEqualTo(200);
        } finally {
            collectorGate.countDown();
            collectorGate = null;
        }
    }

    private HttpResponse<String> request(String traceId) throws Exception {
        return CLIENT.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port
                + "/version?access_token=must-not-export"))
            .timeout(Duration.ofSeconds(2)).header("traceparent", "00-" + traceId + "-0123456789abcdef-01")
            .GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    @AfterAll
    static void closeCollector() {
        COLLECTOR.stop(0);
        ((Logger) LoggerFactory.getLogger(RequestLoggingFilter.class)).detachAppender(ACCESS_LOGS);
        ACCESS_LOGS.stop();
    }
}
