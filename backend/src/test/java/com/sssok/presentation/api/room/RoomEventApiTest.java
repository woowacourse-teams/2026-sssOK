package com.sssok.presentation.api.room;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sssok.application.port.out.RoomRepository;
import com.sssok.domain.room.Room;
import com.sssok.domain.room.RoomCode;
import com.sssok.domain.room.RoomName;
import com.sssok.infrastructure.realtime.InMemorySseEventPublisher;
import com.sssok.support.PostgresContainerSupport;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

// 방 이벤트 구독(SSE)이 실제 PostgreSQL·실제 인증/입장 흐름 위에서 맞물려 도는지 확인하는 인수 테스트.
// PostgresContainerSupport(싱글톤 컨테이너)를 상속하므로 다른 API 인수 테스트와 컨테이너를 공유한다.
@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureMockMvc
class RoomEventApiTest extends PostgresContainerSupport {

    private static final RandomGenerator RANDOM = new SecureRandom();

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    RoomRepository roomRepository;

    @Autowired
    InMemorySseEventPublisher sseEventPublisher;

    // 이 테스트에서 구독을 연 방들. SSE는 타임아웃이 없어(-1) 저절로 안 끝나므로,
    // 각 테스트가 끝나면 열어둔 emitter를 명시적으로 완료 처리해 다음 테스트로 새지 않게 한다.
    private final List<Long> subscribedRoomIds = new ArrayList<>();

    @AfterEach
    void closeOpenEmitters() {
        subscribedRoomIds.forEach(sseEventPublisher::completeAll);
        subscribedRoomIds.clear();
    }

    @Test
    void 구독하면_비동기_스트림이_시작되고_발행한_이벤트를_그대로_받는다() throws Exception {
        Long roomId = 활성_방_저장();
        String accessToken = 익명_인증으로_토큰_발급받기();
        방에_입장하기(roomId, accessToken);

        MvcResult result = mockMvc.perform(get("/api/v1/rooms/{roomId}/events", roomId)
                .header("Authorization", "Bearer " + accessToken))
            .andExpect(request().asyncStarted())
            .andReturn();
        subscribedRoomIds.add(roomId);

        sseEventPublisher.publish(roomId, "media.created", Map.of("mediaId", 5012));

        assertThat(result.getResponse().getContentAsString())
            .contains("event:media.created")
            .contains("5012");
    }

    @Test
    void 구독_중일_때_다른_회원이_실제로_입장하면_room_member_joined_이벤트를_받는다() throws Exception {
        Long roomId = 활성_방_저장();
        String subscriberToken = 익명_인증으로_토큰_발급받기();
        방에_입장하기(roomId, subscriberToken);

        MvcResult result = mockMvc.perform(get("/api/v1/rooms/{roomId}/events", roomId)
                .header("Authorization", "Bearer " + subscriberToken))
            .andExpect(request().asyncStarted())
            .andReturn();
        subscribedRoomIds.add(roomId);

        String joinerToken = 익명_인증으로_토큰_발급받기();
        mockMvc.perform(post("/api/v1/rooms/{roomId}/members", roomId)
                .header("Authorization", "Bearer " + joinerToken))
            .andExpect(status().isCreated());

        assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
            .contains("event:room.member.joined")
            .contains("\"displayName\":\"로지\"");
    }

    @Test
    void token_쿼리_파라미터로도_구독된다() throws Exception {
        Long roomId = 활성_방_저장();
        String accessToken = 익명_인증으로_토큰_발급받기();
        방에_입장하기(roomId, accessToken);

        mockMvc.perform(get("/api/v1/rooms/{roomId}/events", roomId)
                .param("token", accessToken))
            .andExpect(request().asyncStarted());
        subscribedRoomIds.add(roomId);
    }

    @Test
    void 토큰_없이_구독하면_401() throws Exception {
        Long roomId = 활성_방_저장();

        mockMvc.perform(get("/api/v1/rooms/{roomId}/events", roomId))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void 없는_방을_구독하면_404() throws Exception {
        String accessToken = 익명_인증으로_토큰_발급받기();

        mockMvc.perform(get("/api/v1/rooms/{roomId}/events", 999_999L)
                .header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("ROOM_NOT_FOUND"));
    }

    @Test
    void 입장하지_않고_구독하면_403() throws Exception {
        Long roomId = 활성_방_저장();
        String accessToken = 익명_인증으로_토큰_발급받기();

        mockMvc.perform(get("/api/v1/rooms/{roomId}/events", roomId)
                .header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("ROOM_MEMBERSHIP_REQUIRED"));
    }

    private Long 활성_방_저장() {
        Room room = Room.create(RoomCode.generate(RANDOM), new RoomName("실시간방"), 1L, Instant.now());
        return roomRepository.save(room).getId();
    }

    private String 익명_인증으로_토큰_발급받기() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/anonymous")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\":\"로지\"}"))
            .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("data").get("accessToken").asText();
    }

    private void 방에_입장하기(Long roomId, String accessToken) throws Exception {
        mockMvc.perform(post("/api/v1/rooms/{roomId}/members", roomId)
            .header("Authorization", "Bearer " + accessToken));
    }
}
