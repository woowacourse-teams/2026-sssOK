package com.sssok.presentation.api.feedback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sssok.infrastructure.persistence.feedback.FeedbackJpaRepository;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

// API 인수 테스트 — 의견 등록부터 디스코드 웹훅 호출까지 관통 확인한다.
// 디스코드 대신 로컬 HTTP 서버를 웹훅으로 두고, 그 서버가 멈추거나 실패해도 등록이 영향받지 않는지 본다.
@AcceptanceTest
@SpringBootTest(properties = {
    "spring.jpa.hibernate.ddl-auto=validate",
    "feedback.rate-limit-window=0s",
    "notification.discord.read-timeout=10s"
})
@AutoConfigureMockMvc
// 가짜 웹훅 주소 때문에 이 클래스만 쓰는 컨텍스트가 생긴다. 캐시에 남겨 두면 커넥션 풀을 붙든 채
// 공용 PostgreSQL 컨테이너의 접속 상한을 갉아먹으므로 끝나면 닫는다.
@DirtiesContext
class FeedbackNotificationApiTest extends PostgresContainerSupport {

    private static final HttpServer WEBHOOK = startWebhook();
    private static final List<String> RECEIVED = new CopyOnWriteArrayList<>();
    // 웹훅이 응답을 내보내기 전에 붙잡아 두는 문. 열리기 전까지 디스코드가 멈춘 것처럼 보인다.
    private static volatile CountDownLatch release = new CountDownLatch(0);
    private static volatile int responseStatus = 204;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    FeedbackJpaRepository feedbackJpaRepository;

    @DynamicPropertySource
    static void registerWebhook(DynamicPropertyRegistry registry) {
        registry.add("notification.discord.feedback-webhook-url",
            () -> "http://localhost:" + WEBHOOK.getAddress().getPort() + "/webhook");
    }

    @AfterEach
    void tearDown() {
        release.countDown();
        RECEIVED.clear();
        responseStatus = 204;
    }

    @Test
    void 의견을_등록하면_디스코드로_본문과_방_이름과_닉네임이_간다() throws Exception {
        long feedbackId = 의견_등록("가현", "진행률이 멈춘 것처럼 보여요");

        await().atMost(Duration.ofSeconds(5)).until(() -> 받은_의견(feedbackId) != null);
        JsonNode embed = 받은_의견(feedbackId);
        assertThat(embed.get("description").asText()).isEqualTo("진행률이 멈춘 것처럼 보여요");
        assertThat(embed.toString()).contains("우테코 회식", "가현");
    }

    @Test
    void 웹훅이_실패해도_의견은_201로_등록되고_저장된다() throws Exception {
        responseStatus = 500;
        long feedbackId = 의견_등록("가현", "디스코드가 죽어 있어도 남아야 해요");

        await().atMost(Duration.ofSeconds(5)).until(() -> 받은_의견(feedbackId) != null);
        assertThat(feedbackJpaRepository.findById(feedbackId)).isPresent();
    }

    @Test
    void 웹훅이_응답하지_않아도_응답을_기다리지_않고_201을_준다() throws Exception {
        release = new CountDownLatch(1);

        long startedAt = System.nanoTime();
        long feedbackId = 의견_등록("가현", "디스코드가 느려도 바로 끝나야 해요");
        Duration elapsed = Duration.ofNanos(System.nanoTime() - startedAt);

        // 웹훅은 요청을 받아 놓고 10초 동안 응답하지 않는다. 등록이 알림을 기다렸다면 그만큼 걸렸다.
        await().atMost(Duration.ofSeconds(5)).until(() -> 받은_의견(feedbackId) != null);
        assertThat(release.getCount()).isEqualTo(1);
        assertThat(elapsed).isLessThan(Duration.ofSeconds(3));
        assertThat(feedbackJpaRepository.findById(feedbackId)).isPresent();
    }

    private JsonNode 받은_의견(long feedbackId) throws IOException {
        for (String body : RECEIVED) {
            JsonNode embed = objectMapper.readTree(body).get("embeds").get(0);
            if (embed.get("title").asText().equals("새 의견 #" + feedbackId)) {
                return embed;
            }
        }
        return null;
    }

    // 방을 만들고 입장한 뒤 의견을 남긴다. 201 이 아니면 그 자리에서 실패한다.
    private long 의견_등록(String nickname, String content) throws Exception {
        String token = 익명_인증(nickname);
        long roomId = 방_만들고_입장(token);
        MvcResult created = mockMvc.perform(post("/api/v1/rooms/{roomId}/feedbacks", roomId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"" + content + "\"}"))
            .andExpect(status().isCreated())
            .andReturn();
        return Long.parseLong(값(created, "feedbackId"));
    }

    private long 방_만들고_입장(String token) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/rooms")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"우테코 회식\"}"))
            .andReturn();
        long roomId = Long.parseLong(값(created, "roomId"));

        mockMvc.perform(post("/api/v1/rooms/{roomId}/members", roomId)
            .header("Authorization", "Bearer " + token));

        return roomId;
    }

    private String 익명_인증(String nickname) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/anonymous")
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
                try {
                    release.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                exchange.sendResponseHeaders(responseStatus, -1);
                exchange.close();
            });
            // 멈춘 요청이 다음 요청을 막지 않게 요청마다 스레드를 따로 쓴다.
            server.setExecutor(Executors.newCachedThreadPool());
            server.start();
            return server;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
