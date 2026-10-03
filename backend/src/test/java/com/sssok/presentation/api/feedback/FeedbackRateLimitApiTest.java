package com.sssok.presentation.api.feedback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sssok.infrastructure.persistence.feedback.FeedbackJpaRepository;
import com.sssok.support.PostgresContainerSupport;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

// 의견 연속 등록 제한 인수 테스트 — 순차 요청과 동시 요청 모두 실제 PostgreSQL 위에서 확인한다.
@SpringBootTest(properties = {
    "spring.jpa.hibernate.ddl-auto=validate",
    "feedback.rate-limit-window=1m"
})
@AutoConfigureMockMvc
class FeedbackRateLimitApiTest extends PostgresContainerSupport {

    private static final String CHROME_UA =
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) "
            + "Chrome/140.0.0.0 Safari/537.36";
    private static final int THREADS = 8;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    FeedbackJpaRepository feedbackJpaRepository;

    @Test
    void 짧은_시간에_연속으로_등록하면_429와_Retry_After를_받는다() throws Exception {
        String token = 익명_인증("가현");
        long roomId = 방_만들고_입장(token);

        의견_등록(token, roomId, "첫 번째 의견").andExpect(status().isCreated());

        의견_등록(token, roomId, "두 번째 의견")
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.code").value("FEEDBACK_RATE_LIMITED"))
            .andExpect(header().exists(HttpHeaders.RETRY_AFTER));
    }

    @Test
    void 다른_회원은_서로의_제한에_걸리지_않는다() throws Exception {
        String first = 익명_인증("가현");
        String second = 익명_인증("민수");
        long roomId = 방_만들고_입장(first);
        방_입장(second, roomId);

        의견_등록(first, roomId, "가현의 의견").andExpect(status().isCreated());

        의견_등록(second, roomId, "민수의 의견").andExpect(status().isCreated());
    }

    @Test
    void 같은_회원이_동시에_여러_번_등록해도_한_건만_저장되고_나머지는_429와_Retry_After를_받는다()
        throws Exception {
        String token = 익명_인증("가현");
        long roomId = 방_만들고_입장(token);
        long before = feedbackJpaRepository.count();

        List<MockHttpServletResponse> responses = 동시에_의견_등록(List.of(token), roomId);

        List<MockHttpServletResponse> created = responses.stream()
            .filter(response -> response.getStatus() == 201)
            .toList();
        List<MockHttpServletResponse> limited = responses.stream()
            .filter(response -> response.getStatus() == 429)
            .toList();
        assertThat(created).hasSize(1);
        assertThat(limited).hasSize(THREADS - 1);
        assertThat(limited).allSatisfy(response ->
            assertThat(response.getHeader(HttpHeaders.RETRY_AFTER)).isNotBlank());
        assertThat(feedbackJpaRepository.count()).isEqualTo(before + 1);
    }

    @Test
    void 서로_다른_회원이_동시에_등록하면_서로의_제한에_걸리지_않고_모두_저장된다() throws Exception {
        List<String> tokens = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            tokens.add(익명_인증("회원" + i));
        }
        long roomId = 방_만들고_입장(tokens.get(0));
        for (String token : tokens.subList(1, tokens.size())) {
            방_입장(token, roomId);
        }
        long before = feedbackJpaRepository.count();

        List<MockHttpServletResponse> responses = 동시에_의견_등록(tokens, roomId);

        assertThat(responses).extracting(MockHttpServletResponse::getStatus).containsOnly(201);
        assertThat(feedbackJpaRepository.count()).isEqualTo(before + THREADS);
    }

    private List<MockHttpServletResponse> 동시에_의견_등록(List<String> tokens, long roomId)
        throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch 출발 = new CountDownLatch(1);
        try {
            List<Future<MockHttpServletResponse>> futures = new ArrayList<>();
            for (int i = 0; i < THREADS; i++) {
                String token = tokens.get(i % tokens.size());
                futures.add(pool.submit((Callable<MockHttpServletResponse>) () -> {
                    출발.await();
                    return 의견_등록(token, roomId, "동시에 남긴 의견").andReturn().getResponse();
                }));
            }
            출발.countDown();

            List<MockHttpServletResponse> responses = new ArrayList<>();
            for (Future<MockHttpServletResponse> future : futures) {
                responses.add(future.get(30, TimeUnit.SECONDS));
            }
            return responses;
        } finally {
            pool.shutdownNow();
        }
    }

    private ResultActions 의견_등록(String token, long roomId, String content) throws Exception {
        return mockMvc.perform(post("/api/v1/rooms/{roomId}/feedbacks", roomId)
            .header("Authorization", "Bearer " + token)
            .header(HttpHeaders.USER_AGENT, CHROME_UA)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"content\":\"" + content + "\"}"));
    }

    private long 방_만들고_입장(String token) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/rooms")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"우테코 회식\"}"))
            .andReturn();
        long roomId = Long.parseLong(값(created, "roomId"));
        방_입장(token, roomId);
        return roomId;
    }

    private void 방_입장(String token, long roomId) throws Exception {
        mockMvc.perform(post("/api/v1/rooms/{roomId}/members", roomId)
            .header("Authorization", "Bearer " + token));
    }

    private String 값(MvcResult result, String field) throws Exception {
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("data").get(field).asText();
    }

    private String 익명_인증(String nickname) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/anonymous")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\":\"" + nickname + "\"}"))
            .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("data").get("accessToken").asText();
    }
}
