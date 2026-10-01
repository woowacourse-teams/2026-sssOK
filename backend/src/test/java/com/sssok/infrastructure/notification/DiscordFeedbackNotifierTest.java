package com.sssok.infrastructure.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sssok.domain.feedback.Feedback;
import com.sssok.domain.feedback.FeedbackContent;
import com.sssok.domain.feedback.FrontendVersion;
import com.sssok.domain.feedback.UserAgent;
import com.sssok.infrastructure.config.DiscordNotificationProperties;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.observation.DefaultMeterObservationHandler;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.web.client.RestClient;

// 실제 HTTP 서버를 띄워 디스코드 웹훅이 받는 요청 본문을 그대로 확인한다.
@ExtendWith(OutputCaptureExtension.class)
class DiscordFeedbackNotifierTest {

    private static final Instant CREATED_AT = Instant.parse("2026-09-30T05:30:00Z");

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final List<String> receivedBodies = new CopyOnWriteArrayList<>();
    private final List<String> contentLengths = new CopyOnWriteArrayList<>();
    private HttpServer server;
    private int responseStatus;

    @BeforeEach
    void setUp() throws IOException {
        responseStatus = 204;
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/webhook", exchange -> {
            contentLengths.add(exchange.getRequestHeaders().getFirst("Content-Length"));
            receivedBodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(responseStatus, -1);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void 의견_번호와_본문과_방_이름과_닉네임과_작성_시각과_조회_경로를_싣는다() throws Exception {
        notifier(webhookUrl()).notifyCreated(feedback(42L, "진행률이 멈춘 것처럼 보여요", "가현"));

        JsonNode embed = 받은_본문().get("embeds").get(0);
        assertThat(embed.get("title").asText()).isEqualTo("새 의견 #42");
        assertThat(embed.get("description").asText()).isEqualTo("진행률이 멈춘 것처럼 보여요");
        assertThat(embed.get("timestamp").asText()).isEqualTo("2026-09-30T05:30:00Z");
        assertThat(필드(embed, "방")).isEqualTo("우테코 회식");
        assertThat(필드(embed, "닉네임")).isEqualTo("가현");
        assertThat(필드(embed, "작성 시각")).isEqualTo("2026-09-30 14:30:00 (KST)");
        assertThat(필드(embed, "관리자 조회")).contains("/api/v1/admin/feedbacks/42");
    }

    // 길이 헤더만 보고 본문을 읽는 수신 측도 있다. chunked 로 보내면 그쪽은 빈 요청으로 받는다.
    @Test
    void 본문_길이를_Content_Length로_밝혀_보낸다() throws Exception {
        notifier(webhookUrl()).notifyCreated(feedback(1L, "본문", "가현"));

        byte[] body = receivedBodies.get(0).getBytes(StandardCharsets.UTF_8);
        assertThat(contentLengths).containsExactly(String.valueOf(body.length));
    }

    @Test
    void 본문에_멘션이_있어도_아무도_호출되지_않게_한다() throws Exception {
        notifier(webhookUrl()).notifyCreated(feedback(1L, "@everyone 확인해 주세요", "가현"));

        JsonNode allowedMentions = 받은_본문().get("allowed_mentions");
        assertThat(allowedMentions.get("parse")).isEmpty();
    }

    @Test
    void 본문의_마크다운은_해석되지_않게_이스케이프한다() throws Exception {
        notifier(webhookUrl()).notifyCreated(feedback(1L, "[클릭](http://evil.example) **굵게**", "가현"));

        String description = 받은_본문().get("embeds").get(0).get("description").asText();
        assertThat(description).isEqualTo("\\[클릭\\]\\(http://evil.example\\) \\*\\*굵게\\*\\*");
    }

    @Test
    void 긴_본문은_300자에서_자르고_말줄임표를_붙인다() throws Exception {
        notifier(webhookUrl()).notifyCreated(feedback(1L, "가".repeat(1000), "가현"));

        String description = 받은_본문().get("embeds").get(0).get("description").asText();
        assertThat(description).isEqualTo("가".repeat(300) + "…");
    }

    @Test
    void 이모지는_반으로_갈리지_않는다() throws Exception {
        notifier(webhookUrl()).notifyCreated(feedback(1L, "😀".repeat(301), "가현"));

        String description = 받은_본문().get("embeds").get(0).get("description").asText();
        assertThat(description).isEqualTo("😀".repeat(300) + "…");
    }

    @Test
    void 닉네임이_없으면_알_수_없음으로_싣는다() throws Exception {
        notifier(webhookUrl()).notifyCreated(feedback(1L, "탈퇴한 회원의 의견", null));

        assertThat(필드(받은_본문().get("embeds").get(0), "닉네임")).isEqualTo("알 수 없음");
    }

    @Test
    void 웹훅이_실패_응답을_주어도_예외를_올리지_않는다() {
        responseStatus = 500;

        assertThatCode(() -> notifier(webhookUrl()).notifyCreated(feedback(1L, "본문", "가현")))
            .doesNotThrowAnyException();
        assertThat(receivedBodies).hasSize(1);
    }

    @Test
    void 웹훅에_연결할_수_없어도_예외를_올리지_않는다() {
        server.stop(0);

        assertThatCode(() -> notifier(webhookUrl()).notifyCreated(feedback(1L, "본문", "가현")))
            .doesNotThrowAnyException();
    }

    // 웹훅 URL 은 채널에 글을 쓸 수 있는 비밀값이다. 연결 실패 예외 메시지에는 요청 URL 이 들어가므로
    // 그 메시지를 그대로 로그에 남기면 안 된다.
    @Test
    void 연결_실패_로그에_웹훅_URL을_남기지_않는다(CapturedOutput output) {
        String webhookUrl = webhookUrl();
        server.stop(0);

        notifier(webhookUrl).notifyCreated(feedback(1L, "본문", "가현"));

        assertThat(output).contains("디스코드 의견 알림 전송에 실패했습니다", "ResourceAccessException");
        assertThat(output).doesNotContain(webhookUrl);
    }

    @Test
    void 실패_응답_로그에는_상태_코드만_남기고_웹훅_URL은_남기지_않는다(CapturedOutput output) {
        responseStatus = 500;

        notifier(webhookUrl()).notifyCreated(feedback(1L, "본문", "가현"));

        assertThat(output).contains("status=500");
        assertThat(output).doesNotContain(webhookUrl());
    }

    // 웹훅 URL 은 경로 끝이 토큰이다. 스프링의 HTTP 클라이언트 메트릭이 경로를 태그로 실으면
    // Prometheus 로 수집돼 그대로 남는다.
    @Test
    void HTTP_클라이언트_메트릭_태그에_웹훅_경로를_남기지_않는다() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        ObservationRegistry observationRegistry = ObservationRegistry.create();
        observationRegistry.observationConfig()
            .observationHandler(new DefaultMeterObservationHandler(meterRegistry));
        DiscordNotificationProperties properties = new DiscordNotificationProperties(
            webhookUrl(), Duration.ofSeconds(1), Duration.ofSeconds(1));

        new DiscordFeedbackNotifier(properties, RestClient.builder().observationRegistry(observationRegistry))
            .notifyCreated(feedback(1L, "본문", "가현"));

        Timer timer = meterRegistry.get("http.client.requests").timer();
        assertThat(timer.getId().getTag("uri")).isEqualTo("/discord-webhook");
        assertThat(timer.getId().getTag("status")).isEqualTo("204");
        assertThat(meterRegistry.getMeters())
            .flatMap(meter -> meter.getId().getTags())
            .noneMatch(tag -> tag.getValue().contains("/webhook"));
    }

    @Test
    void 웹훅_URL이_없으면_요청을_보내지_않는다() {
        notifier(" ").notifyCreated(feedback(1L, "본문", "가현"));
        notifier(null).notifyCreated(feedback(1L, "본문", "가현"));

        assertThat(receivedBodies).isEmpty();
    }

    private DiscordFeedbackNotifier notifier(String webhookUrl) {
        DiscordNotificationProperties properties = new DiscordNotificationProperties(
            webhookUrl, Duration.ofSeconds(1), Duration.ofSeconds(1));
        return new DiscordFeedbackNotifier(properties, RestClient.builder());
    }

    private String webhookUrl() {
        return "http://localhost:" + server.getAddress().getPort() + "/webhook";
    }

    private JsonNode 받은_본문() throws Exception {
        assertThat(receivedBodies).hasSize(1);
        return objectMapper.readTree(receivedBodies.get(0));
    }

    private static String 필드(JsonNode embed, String name) {
        for (JsonNode field : embed.get("fields")) {
            if (field.get("name").asText().equals(name)) {
                return field.get("value").asText();
            }
        }
        throw new AssertionError("필드가 없습니다: " + name);
    }

    private static Feedback feedback(Long id, String content, String nickname) {
        return Feedback.reconstruct(
            id,
            new FeedbackContent(content),
            7L,
            "우테코 회식",
            3L,
            nickname,
            UserAgent.from(null),
            FrontendVersion.from(null),
            CREATED_AT
        );
    }
}
