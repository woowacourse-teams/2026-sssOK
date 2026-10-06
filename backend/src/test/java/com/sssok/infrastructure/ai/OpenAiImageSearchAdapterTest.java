package com.sssok.infrastructure.ai;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sssok.application.port.out.ImageDescriptionPort;
import com.sssok.application.port.out.TextEmbeddingPort;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sssok.infrastructure.config.OpenAiImageSearchProperties;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OpenAiImageSearchAdapterTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private HttpServer server;
    private ExecutorService executor;
    private OpenAiImageSearchProperties properties;
    private OpenAiImageDescriptionAdapter descriptions;
    private OpenAiTextEmbeddingAdapter embeddings;
    private volatile String response;
    private volatile int responseStatus;
    private volatile long delayMillis;
    private volatile JsonNode request;
    private volatile String path;
    private volatile String authorization;

    @BeforeEach
    void setup() throws Exception {
        responseStatus = 200;
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        executor = Executors.newCachedThreadPool();
        server.setExecutor(executor);
        server.createContext("/v1", exchange -> {
            try {
                request = mapper.readTree(exchange.getRequestBody());
                path = exchange.getRequestURI().getPath();
                authorization = exchange.getRequestHeaders().getFirst("Authorization");
                if (delayMillis > 0) {
                    try { Thread.sleep(delayMillis); }
                    catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); return; }
                }
                byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(responseStatus, bytes.length);
                exchange.getResponseBody().write(bytes);
            } finally { exchange.close(); }
        });
        server.start();
        properties = new OpenAiImageSearchProperties("test-key", URI.create(
            "http://127.0.0.1:" + server.getAddress().getPort() + "/v1"), null, null, 3, null, null);
        OpenAiSearchClient client = new OpenAiSearchClient(properties);
        descriptions = new OpenAiImageDescriptionAdapter(client, properties, mapper);
        embeddings = new OpenAiTextEmbeddingAdapter(client, properties);
    }

    @AfterEach
    void cleanup() {
        if (server != null) server.stop(0);
        if (executor != null) executor.shutdownNow();
    }

    @Test
    void 추론과_저장을_끄고_스키마에_맞는_이미지_설명을_받는다() throws Exception {
        response = descriptionResponse("completed", "output_text",
            "{\"description\":\"해변 단체 사진\",\"features\":[\"바다\",\"사람\"]}");
        ImageDescriptionPort.Description result = descriptions.describe("https://signed.test/image", Duration.ofSeconds(3));
        assertThat(result.description()).isEqualTo("해변 단체 사진");
        assertThat(result.features()).isEqualTo("바다, 사람");
        assertThat(result.promptVersion()).isEqualTo("image-search-v1");
        assertThat(path).isEqualTo("/v1/responses");
        assertThat(authorization).isEqualTo("Bearer test-key");
        assertThat(request.path("model").asText()).isEqualTo("gpt-6-luna");
        assertThat(request.path("store").asBoolean()).isFalse();
        assertThat(request.at("/reasoning/effort").asText()).isEqualTo("none");
        assertThat(request.at("/text/format/strict").asBoolean()).isTrue();
        assertThat(request.at("/input/0/content/0/detail").asText()).isEqualTo("low");
        assertThat(request.path("max_output_tokens").asInt()).isEqualTo(384);
    }

    @Test
    void 임베딩_요청의_모델과_차원을_고정하고_벡터를_반환한다() {
        response = embeddingResponse("[1.0,0.0,0.0]");
        TextEmbeddingPort.Embedding result = embeddings.embed("해변 사진", Duration.ofSeconds(3));
        assertThat(path).isEqualTo("/v1/embeddings");
        assertThat(request.path("dimensions").asInt()).isEqualTo(3);
        assertThat(request.path("encoding_format").asText()).isEqualTo("float");
        assertThat(result.values()).containsExactly(1.0, 0.0, 0.0);
        assertThat(result.model()).isEqualTo("text-embedding-3-small");
    }

    @Test
    void 거절과_잘린_설명은_완료_결과로_저장하지_않는다() throws Exception {
        for (String status : new String[] {"incomplete", "failed"}) {
            response = descriptionResponse(status, "output_text", "{}");
            assertThatThrownBy(() -> descriptions.describe("https://signed.test/image", Duration.ofSeconds(3)))
                .isInstanceOf(OpenAiCallException.class);
        }
        response = descriptionResponse("completed", "refusal", "refused");
        assertThatThrownBy(() -> descriptions.describe("https://signed.test/image", Duration.ofSeconds(3)))
            .hasMessageContaining("DESCRIPTION_REFUSED");
    }

    @Test
    void 잘못된_JSON과_빈_특징을_거절한다() throws Exception {
        for (String text : new String[] {"not-json", "{\"description\":\"사진\",\"features\":[]}"}) {
            response = descriptionResponse("completed", "output_text", text);
            assertThatThrownBy(() -> descriptions.describe("https://signed.test/image", Duration.ofSeconds(3)))
                .isInstanceOf(OpenAiCallException.class);
        }
    }

    @Test
    void 다른_차원과_영벡터와_잘못된_수치를_거절한다() {
        for (String vector : new String[] {"[1,0]", "[0,0,0]", "[1,\"bad\",0]", "[1e100,0,0]"}) {
            response = embeddingResponse(vector);
            assertThatThrownBy(() -> embeddings.embed("사진", Duration.ofSeconds(3)))
                .isInstanceOf(OpenAiCallException.class);
        }
    }

    @Test
    void 제공자_HTTP_오류의_본문과_키를_예외에_포함하지_않는다() {
        for (int status : new int[] {401, 429, 500}) {
            responseStatus = status;
            response = "{\"error\":\"secret response test-key https://signed.test/image\"}";
            assertThatThrownBy(() -> embeddings.embed("사진", Duration.ofSeconds(3)))
                .isInstanceOf(OpenAiCallException.class).hasMessageContaining("HTTP_" + status)
                .hasMessageNotContaining("test-key").hasMessageNotContaining("signed.test")
                .hasMessageNotContaining("secret response").hasCause(null);
        }
    }

    @Test
    void 실제_HTTP_응답_대기에_타임아웃을_적용한다() {
        response = embeddingResponse("[1,0,0]");
        delayMillis = 1000;
        long started = System.nanoTime();
        assertThatThrownBy(() -> embeddings.embed("사진", Duration.ofMillis(100)))
            .isInstanceOf(OpenAiCallException.class);
        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(3));
    }

    @Test
    void 설정_문자열에서_키를_가리고_키_누락은_시작_전에_검사한다() {
        assertThat(properties.toString()).doesNotContain("test-key");
        assertThatThrownBy(() -> new OpenAiSearchClient(new OpenAiImageSearchProperties(
            null, null, null, null, null, null, null)))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("OPENAI_API_KEY");
    }

    private String descriptionResponse(String status, String type, String text) throws Exception {
        ObjectNode result = mapper.createObjectNode().put("status", status).put("model", "gpt-6-luna");
        result.putArray("output").addObject().putArray("content").addObject()
            .put("type", type).put("text", text);
        return mapper.writeValueAsString(result);
    }

    private String embeddingResponse(String vector) {
        return "{\"model\":\"text-embedding-3-small\",\"data\":[{\"index\":0,\"embedding\":" + vector + "}]}";
    }
}
