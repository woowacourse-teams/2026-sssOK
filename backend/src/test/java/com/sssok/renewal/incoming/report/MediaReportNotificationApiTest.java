package com.sssok.renewal.incoming.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sssok.support.AcceptanceTest;
import com.sssok.support.PostgresContainerSupport;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

// 디스코드 대신 로컬 HTTP 서버를 의견 웹훅으로 두고 신고 알림을 받는다.
@AcceptanceTest
@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureMockMvc
// 이 클래스만 쓰는 컨텍스트라 공용 PostgreSQL 접속 상한을 갉아먹지 않게 끝나면 닫는다.
@DirtiesContext
class MediaReportNotificationApiTest extends PostgresContainerSupport {

    private static final HttpServer WEBHOOK = startWebhook();
    private static final List<String> RECEIVED = new CopyOnWriteArrayList<>();
    private static final AtomicLong MEDIA_ID_SEQUENCE = new AtomicLong(System.currentTimeMillis() + 3_000_000);
    private static volatile int responseStatus = 204;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void registerWebhook(DynamicPropertyRegistry registry) {
        registry.add("notification.discord.feedback-webhook-url",
            () -> "http://localhost:" + WEBHOOK.getAddress().getPort() + "/webhook");
    }

    @AfterEach
    void tearDown() {
        RECEIVED.clear();
        responseStatus = 204;
    }

    @Test
    void 신고가_저장되면_디스코드로_방과_신고자와_사유와_조회_경로가_간다() throws Exception {
        String token = 익명_인증("가현");
        long roomId = 방_만들고_입장(token);
        long mediaId = 존재하는_미디어(roomId);

        신고(token, roomId, mediaId).andExpect(status().isCreated());
        long reportId = 신고_ID(mediaId);

        await().atMost(Duration.ofSeconds(5)).until(() -> 받은_신고(reportId) != null);
        JsonNode embed = 받은_신고(reportId);
        assertThat(embed.get("description").asText()).isEqualTo("개인정보·초상권 침해");
        assertThat(embed.toString())
            .contains("우테코 회식", "가현", String.valueOf(mediaId), "/api/v1/admin/media-reports/" + reportId);
    }

    @Test
    void 웹훅이_실패해도_신고는_201로_접수되고_남는다() throws Exception {
        responseStatus = 500;
        String token = 익명_인증("가현");
        long roomId = 방_만들고_입장(token);
        long mediaId = 존재하는_미디어(roomId);

        신고(token, roomId, mediaId).andExpect(status().isCreated());
        long reportId = 신고_ID(mediaId);

        await().atMost(Duration.ofSeconds(5)).until(() -> 받은_신고(reportId) != null);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT count(*) FROM media_report WHERE id = ?", Long.class, reportId)).isEqualTo(1);
    }

    @Test
    void 저장되지_않은_중복_신고는_알리지_않는다() throws Exception {
        String token = 익명_인증("가현");
        long roomId = 방_만들고_입장(token);
        long mediaId = 존재하는_미디어(roomId);
        신고(token, roomId, mediaId).andExpect(status().isCreated());
        await().atMost(Duration.ofSeconds(5)).until(() -> RECEIVED.size() == 1);

        신고(token, roomId, mediaId).andExpect(status().isConflict());

        await().during(Duration.ofSeconds(1)).atMost(Duration.ofSeconds(2)).until(() -> RECEIVED.size() == 1);
    }

    private JsonNode 받은_신고(long reportId) throws IOException {
        for (String body : RECEIVED) {
            JsonNode embed = objectMapper.readTree(body).get("embeds").get(0);
            if (embed.get("title").asText().equals("새 사진 신고 #" + reportId)) {
                return embed;
            }
        }
        return null;
    }

    private ResultActions 신고(String token, long roomId, long mediaId) throws Exception {
        return mockMvc.perform(post("/api/v1/rooms/{roomId}/media/{mediaId}/reports", roomId, mediaId)
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"reason\":\"PRIVACY_VIOLATION\"}"));
    }

    private long 신고_ID(long mediaId) {
        return jdbcTemplate.queryForObject("SELECT id FROM media_report WHERE media_id = ?", Long.class, mediaId);
    }

    private long 존재하는_미디어(long roomId) {
        long mediaId = MEDIA_ID_SEQUENCE.incrementAndGet();
        jdbcTemplate.update("""
            INSERT INTO stored_file
                (id, room_id, uploader_id, original_file_name, media_type, file_size_bytes,
                 storage_key, status, created_at, updated_at, reserved_at, retry_count)
            VALUES (?, ?, 1, 'test.jpg', 'JPEG', 1024, ?, 'READY', now(), now(), now(), 0)
            """, mediaId, roomId, "report-notification-test-key-" + mediaId);
        return mediaId;
    }

    private long 방_만들고_입장(String token) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/rooms")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"우테코 회식\"}"))
            .andReturn();
        return Long.parseLong(값(created, "roomId"));
    }

    private String 익명_인증(String nickname) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/anonymous").header("X-CSRF-Protection", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\":\"" + nickname + "\"}"))
            .andReturn();
        return 값(result, "accessToken");
    }

    private String 값(MvcResult result, String field) throws Exception {
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("data").get(field).asText();
    }

    private static HttpServer startWebhook() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/webhook", exchange -> {
                RECEIVED.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                exchange.sendResponseHeaders(responseStatus, -1);
                exchange.close();
            });
            server.setExecutor(Executors.newCachedThreadPool());
            server.start();
            return server;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
