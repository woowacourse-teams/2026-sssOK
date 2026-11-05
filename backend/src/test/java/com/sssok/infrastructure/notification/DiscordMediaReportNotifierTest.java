package com.sssok.infrastructure.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sssok.infrastructure.config.DiscordNotificationProperties;
import com.sssok.renewal.domain.report.MediaReport;
import com.sssok.renewal.domain.report.MediaReportReason;
import com.sssok.renewal.domain.report.MediaReportStatus;
import com.sun.net.httpserver.HttpServer;
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

@ExtendWith(OutputCaptureExtension.class)
class DiscordMediaReportNotifierTest {

    private static final Instant REPORTED_AT = Instant.parse("2026-11-03T05:30:00Z");

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final List<String> receivedBodies = new CopyOnWriteArrayList<>();
    private HttpServer server;
    private int responseStatus;

    @BeforeEach
    void setUp() throws IOException {
        responseStatus = 204;
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/webhook", exchange -> {
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
    void 신고_번호와_사유와_방과_신고자와_미디어와_접수_시각과_조회_경로를_싣는다() throws Exception {
        notifier(webhookUrl()).notifyReported(report(42L, MediaReportReason.HARASSMENT));

        JsonNode body = 받은_본문();
        JsonNode embed = body.get("embeds").get(0);
        assertThat(embed.get("title").asText()).isEqualTo("새 사진 신고 #42");
        assertThat(embed.get("description").asText()).isEqualTo("괴롭힘·혐오 표현");
        assertThat(embed.get("timestamp").asText()).isEqualTo("2026-11-03T05:30:00Z");
        assertThat(필드(embed, "방")).isEqualTo("우테코 회식 (roomId=7)");
        assertThat(필드(embed, "신고자")).isEqualTo("가현");
        assertThat(필드(embed, "미디어 ID")).isEqualTo("99");
        assertThat(필드(embed, "접수 시각")).isEqualTo("2026-11-03 14:30:00 (KST)");
        assertThat(필드(embed, "관리자 조회")).contains("/api/v1/admin/media-reports/42");
        assertThat(body.get("allowed_mentions").get("parse")).isEmpty();
    }

    @Test
    void 방_이름과_닉네임은_마크다운이_해석되지_않게_이스케이프하고_없으면_알_수_없음으로_싣는다() throws Exception {
        notifier(webhookUrl()).notifyReported(report(1L, "**굵게**", "_기울임_"));
        notifier(webhookUrl()).notifyReported(report(2L, null, null));

        assertThat(필드(본문(0), "방")).startsWith("\\*\\*굵게\\*\\*");
        assertThat(필드(본문(0), "신고자")).isEqualTo("\\_기울임\\_");
        assertThat(필드(본문(1), "방")).startsWith("알 수 없음");
        assertThat(필드(본문(1), "신고자")).isEqualTo("알 수 없음");
    }

    @Test
    void 웹훅이_실패해도_예외를_올리지_않고_URL_없이_상태만_남긴다(CapturedOutput output) {
        responseStatus = 500;

        assertThatCode(() -> notifier(webhookUrl()).notifyReported(report(1L, MediaReportReason.HARASSMENT)))
            .doesNotThrowAnyException();
        assertThat(output).contains("디스코드 신고 알림 전송에 실패했습니다", "reportId=1", "status=500");
        assertThat(output).doesNotContain(webhookUrl());
    }

    @Test
    void 웹훅_URL이_없으면_요청을_보내지_않는다() {
        notifier(" ").notifyReported(report(1L, MediaReportReason.HARASSMENT));

        assertThat(receivedBodies).isEmpty();
    }

    private MediaReport report(Long id, MediaReportReason reason) {
        return MediaReport.reconstruct(id, 7L, "우테코 회식", 99L, 3L, "가현", reason, REPORTED_AT,
            MediaReportStatus.UNREFLECTED, null);
    }

    private MediaReport report(Long id, String roomName, String nickname) {
        return MediaReport.reconstruct(id, 7L, roomName, 99L, 3L, nickname, MediaReportReason.HARASSMENT,
            REPORTED_AT, MediaReportStatus.UNREFLECTED, null);
    }

    private DiscordMediaReportNotifier notifier(String webhookUrl) {
        DiscordNotificationProperties properties = new DiscordNotificationProperties(
            webhookUrl, Duration.ofSeconds(1), Duration.ofSeconds(1));
        return new DiscordMediaReportNotifier(properties, RestClient.builder());
    }

    private String webhookUrl() {
        return "http://localhost:" + server.getAddress().getPort() + "/webhook";
    }

    private JsonNode 받은_본문() throws Exception {
        assertThat(receivedBodies).hasSize(1);
        return objectMapper.readTree(receivedBodies.get(0));
    }

    private JsonNode 본문(int index) throws Exception {
        return objectMapper.readTree(receivedBodies.get(index)).get("embeds").get(0);
    }

    private static String 필드(JsonNode embed, String name) {
        for (JsonNode field : embed.get("fields")) {
            if (field.get("name").asText().equals(name)) {
                return field.get("value").asText();
            }
        }
        throw new AssertionError("필드가 없습니다: " + name);
    }
}
