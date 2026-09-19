package com.sssok.presentation.api.feedback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sssok.application.port.out.MemberRepository;
import com.sssok.application.port.out.RoomRepository;
import com.sssok.application.room.PurgeRoomService;
import com.sssok.domain.room.Room;
import com.sssok.infrastructure.persistence.feedback.FeedbackJpaEntity;
import com.sssok.infrastructure.persistence.feedback.FeedbackJpaRepository;
import com.sssok.support.PostgresContainerSupport;
import java.time.Duration;
import java.time.Instant;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

// API 인수 테스트 — 방 생성/입장부터 의견 등록까지 실제 PostgreSQL 위에서 관통 확인한다.
@SpringBootTest(properties = {
    "spring.jpa.hibernate.ddl-auto=validate",
    "feedback.rate-limit-window=0s"
})
@AutoConfigureMockMvc
class FeedbackApiTest extends PostgresContainerSupport {

    private static final Duration RETENTION = Duration.ofDays(7);

    private static final String CHROME_UA =
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) "
            + "Chrome/140.0.0.0 Safari/537.36";
    private static final String APP_VERSION = "fe-v0.1.0";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    FeedbackJpaRepository feedbackJpaRepository;

    @Autowired
    PurgeRoomService purgeRoomService;

    @Autowired
    RoomRepository roomRepository;

    @Autowired
    MemberRepository memberRepository;

    @Test
    void 입장한_사용자가_의견을_남기면_201과_식별자를_받는다() throws Exception {
        String token = 익명_인증("가현");
        long roomId = 방_만들고_입장(token);

        의견_등록(token, roomId, "{\"content\":\"진행률이 멈춘 것처럼 보여요\"}", CHROME_UA)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.feedbackId").value(Matchers.notNullValue()))
            .andExpect(jsonPath("$.data.createdAt").value(Matchers.notNullValue()));
    }

    @Test
    void 방_번호와_방_이름과_회원_번호와_닉네임이_함께_저장된다() throws Exception {
        String token = 익명_인증("가현");
        long roomId = 방_만들고_입장(token);

        long feedbackId = 의견_남기기(token, roomId, "폴더 이름을 더 길게 쓰고 싶어요", CHROME_UA);

        FeedbackJpaEntity saved = feedbackJpaRepository.findById(feedbackId).orElseThrow();
        assertThat(saved.getContent()).isEqualTo("폴더 이름을 더 길게 쓰고 싶어요");
        assertThat(saved.getRoomId()).isEqualTo(roomId);
        assertThat(saved.getRoomName()).isEqualTo("우테코 회식");
        assertThat(saved.getMemberId()).isNotNull();
        assertThat(saved.getNickname()).isEqualTo("가현");
        assertThat(saved.getAppVersion()).isEqualTo(APP_VERSION);
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void User_Agent_헤더_원본이_그대로_저장된다() throws Exception {
        String token = 익명_인증("가현");
        long roomId = 방_만들고_입장(token);

        long feedbackId = 의견_남기기(token, roomId, "아이폰에서 썸네일이 늦게 떠요", CHROME_UA);

        FeedbackJpaEntity saved = feedbackJpaRepository.findById(feedbackId).orElseThrow();
        assertThat(saved.getUserAgent()).isEqualTo(CHROME_UA);
    }

    @Test
    void User_Agent가_없어도_등록에_성공하고_빈_값으로_저장된다() throws Exception {
        String token = 익명_인증("가현");
        long roomId = 방_만들고_입장(token);

        long feedbackId = 의견_남기기(token, roomId, "curl 로 보냈어요", null);

        FeedbackJpaEntity saved = feedbackJpaRepository.findById(feedbackId).orElseThrow();
        assertThat(saved.getUserAgent()).isNull();
    }

    @Test
    void 아주_긴_User_Agent는_잘려서_저장된다() throws Exception {
        String token = 익명_인증("가현");
        long roomId = 방_만들고_입장(token);

        long feedbackId = 의견_남기기(token, roomId, "확장이 많은 브라우저예요", "A".repeat(700));

        FeedbackJpaEntity saved = feedbackJpaRepository.findById(feedbackId).orElseThrow();
        assertThat(saved.getUserAgent()).hasSize(512);
    }

    @Test
    void X_App_Version_헤더_값이_그대로_저장된다() throws Exception {
        String token = 익명_인증("가현");
        long roomId = 방_만들고_입장(token);

        long feedbackId = 의견_남기기(token, roomId, "0.1.0 에서 겪은 일이에요", CHROME_UA);

        FeedbackJpaEntity saved = feedbackJpaRepository.findById(feedbackId).orElseThrow();
        assertThat(saved.getAppVersion()).isEqualTo(APP_VERSION);
    }

    @Test
    void X_App_Version이_없어도_등록에_성공하고_빈_값으로_저장된다() throws Exception {
        String token = 익명_인증("가현");
        long roomId = 방_만들고_입장(token);

        MvcResult created = 의견_등록(
            token, roomId, "{\"content\":\"버전 헤더 없이 보냅니다\"}", CHROME_UA, null)
            .andExpect(status().isCreated())
            .andReturn();

        FeedbackJpaEntity saved = feedbackJpaRepository
            .findById(Long.parseLong(값(created, "feedbackId"))).orElseThrow();
        assertThat(saved.getAppVersion()).isNull();
    }

    @Test
    void 아주_긴_X_App_Version은_잘려서_저장된다() throws Exception {
        String token = 익명_인증("가현");
        long roomId = 방_만들고_입장(token);

        MvcResult created = 의견_등록(
            token, roomId, "{\"content\":\"긴 버전 태그\"}", CHROME_UA, "v".repeat(100))
            .andReturn();

        FeedbackJpaEntity saved = feedbackJpaRepository
            .findById(Long.parseLong(값(created, "feedbackId"))).orElseThrow();
        assertThat(saved.getAppVersion()).hasSize(32);
    }

    @Test
    void 본문이_비어_있으면_400() throws Exception {
        String token = 익명_인증("가현");
        long roomId = 방_만들고_입장(token);

        의견_등록(token, roomId, "{\"content\":\"   \"}", CHROME_UA)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_FEEDBACK_CONTENT"));
    }

    @Test
    void 본문이_1000자를_넘으면_400() throws Exception {
        String token = 익명_인증("가현");
        long roomId = 방_만들고_입장(token);

        의견_등록(token, roomId, "{\"content\":\"" + "가".repeat(1001) + "\"}", CHROME_UA)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("FEEDBACK_TOO_LONG"));
    }

    @Test
    void 입장하지_않은_사용자가_의견을_남기면_403() throws Exception {
        String hostToken = 익명_인증("가현");
        String guestToken = 익명_인증("민수");
        long roomId = 방_만들고_입장(hostToken);

        의견_등록(guestToken, roomId, "{\"content\":\"끼어들기\"}", CHROME_UA)
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("NOT_ROOM_MEMBER"));
    }

    @Test
    void 없는_방에_의견을_남기면_404() throws Exception {
        String token = 익명_인증("가현");

        의견_등록(token, -1L, "{\"content\":\"없는 방\"}", CHROME_UA)
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("ROOM_NOT_FOUND"));
    }

    @Test
    void 삭제된_방에_의견을_남기면_410() throws Exception {
        String token = 익명_인증("가현");
        long roomId = 방_만들고_입장(token);

        방_삭제(token, roomId).andExpect(status().isOk());

        의견_등록(token, roomId, "{\"content\":\"이미 끝난 방\"}", CHROME_UA)
            .andExpect(status().isGone())
            .andExpect(jsonPath("$.code").value("ROOM_EXPIRED"));
    }
    
    @Test
    void 방을_purge_해도_의견과_맥락_값이_남는다() throws Exception {
        String token = 익명_인증("가현");
        long roomId = 방_만들고_입장(token);
        long feedbackId = 의견_남기기(token, roomId, "방이 사라져도 이 의견은 남아야 해요", CHROME_UA);
        Long memberId = feedbackJpaRepository.findById(feedbackId).orElseThrow().getMemberId();

        방_삭제(token, roomId).andExpect(status().isOk());
        보존_기간이_지난_삭제로_되돌리기(roomId);

        purgeRoomService.purgeAll(Instant.now());

        assertThat(roomRepository.findById(roomId)).isEmpty();
        assertThat(memberRepository.findById(memberId)).isEmpty();

        FeedbackJpaEntity survived = feedbackJpaRepository.findById(feedbackId).orElseThrow();
        assertThat(survived.getContent()).isEqualTo("방이 사라져도 이 의견은 남아야 해요");
        assertThat(survived.getRoomId()).isEqualTo(roomId);
        assertThat(survived.getRoomName()).isEqualTo("우테코 회식");
        assertThat(survived.getMemberId()).isEqualTo(memberId);
        assertThat(survived.getNickname()).isEqualTo("가현");
        assertThat(survived.getCreatedAt()).isNotNull();
    }

    @Test
    void 인증_없이_의견을_남기면_401() throws Exception {
        String token = 익명_인증("가현");
        long roomId = 방_만들고_입장(token);

        mockMvc.perform(post("/api/v1/rooms/{roomId}/feedbacks", roomId)
                .header(HttpHeaders.USER_AGENT, CHROME_UA)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"토큰 없음\"}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    private ResultActions 의견_등록(String token, long roomId, String body, String userAgent)
        throws Exception {
        return 의견_등록(token, roomId, body, userAgent, APP_VERSION);
    }

    private ResultActions 의견_등록(
        String token, long roomId, String body, String userAgent, String appVersion
    ) throws Exception {
        var request = post("/api/v1/rooms/{roomId}/feedbacks", roomId)
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body);
        if (userAgent != null) {
            request = request.header(HttpHeaders.USER_AGENT, userAgent);
        }
        if (appVersion != null) {
            request = request.header("X-App-Version", appVersion);
        }
        return mockMvc.perform(request);
    }

    private long 의견_남기기(String token, long roomId, String content, String userAgent)
        throws Exception {
        MvcResult created = 의견_등록(
            token, roomId, "{\"content\":\"" + content + "\"}", userAgent).andReturn();
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

    private ResultActions 방_삭제(String token, long roomId) throws Exception {
        return mockMvc.perform(delete("/api/v1/rooms/{roomId}", roomId)
            .header("Authorization", "Bearer " + token));
    }

    private void 보존_기간이_지난_삭제로_되돌리기(long roomId) {
        Room stored = roomRepository.findById(roomId).orElseThrow();
        roomRepository.save(Room.reconstruct(
            stored.getId(),
            stored.getVersion(),
            stored.getCode(),
            stored.getName(),
            stored.getStatus(),
            stored.getExpiration(),
            stored.getUploadPolicy(),
            stored.getHostId(),
            stored.getCreatedAt(),
            Instant.now().minus(RETENTION).minus(Duration.ofDays(1))
        ));
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

    @SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "feedback.rate-limit-window=1m"
    })
    @AutoConfigureMockMvc
    static class 연속_등록_제한 extends PostgresContainerSupport {

        @Autowired
        MockMvc mockMvc;

        @Autowired
        ObjectMapper objectMapper;

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
}
