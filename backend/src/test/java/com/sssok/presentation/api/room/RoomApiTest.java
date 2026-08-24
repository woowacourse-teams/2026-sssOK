package com.sssok.presentation.api.room;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sssok.support.PostgresContainerSupport;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

// API 인수 테스트 — 생성부터 삭제까지 실제 PostgreSQL 위에서 관통 확인한다.
@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureMockMvc
class RoomApiTest extends PostgresContainerSupport {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void 방을_생성하면_roomId와_방장_정보가_함께_내려오고_코드로_조회된다() throws Exception {
        String token = 익명_인증("가현");

        MvcResult created = 방_생성(token, "{\"name\":\"우테코 회식\"}")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.roomId").value(notNullValue()))
            .andExpect(jsonPath("$.data.code").value(matchesPattern("[23456789ABCDEFGHJKLMNPQRSTUVWXYZ]{8}")))
            .andExpect(jsonPath("$.data.name").value("우테코 회식"))
            .andExpect(jsonPath("$.data.status").value("ACTIVE"))
            .andExpect(jsonPath("$.data.hostId").value(notNullValue()))
            .andExpect(jsonPath("$.data.hostName").value("가현"))
            .andExpect(jsonPath("$.data.uploadPolicy").value("everyone"))
            .andExpect(jsonPath("$.data.requiresPasscode").value(false))
            .andExpect(jsonPath("$.data.joined").value(false))
            .andExpect(jsonPath("$.data.expiresAt").value(notNullValue()))
            .andReturn();

        String code = 값(created, "code");

        mockMvc.perform(get("/api/v1/rooms/{code}", code))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.code").value(code))
            .andExpect(jsonPath("$.data.name").value("우테코 회식"))
            .andExpect(jsonPath("$.data.hostName").value("가현"));
    }

    @Test
    void 인증_없이_방을_생성하면_401() throws Exception {
        mockMvc.perform(post("/api/v1/rooms")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"우테코 회식\"}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void 이름_없이_생성하면_400() throws Exception {
        방_생성(익명_인증("가현"), "{\"name\":\"\"}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_ROOM_NAME"));
    }

    @Test
    void 이름이_12자를_넘으면_400() throws Exception {
        방_생성(익명_인증("가현"), "{\"name\":\"" + "가".repeat(13) + "\"}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_ROOM_NAME"));
    }

    @Test
    void 공백만_넣은_암호로_생성하면_400() throws Exception {
        방_생성(익명_인증("가현"), "{\"name\":\"우테코 회식\",\"entryPassword\":\"   \"}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_ENTRY_PASSWORD"));
    }

    @Test
    void roomId_자리에_방_코드를_넣으면_500이_아니라_400() throws Exception {
        String token = 익명_인증("가현");
        생성된_방 room = 방_만들기(token);

        mockMvc.perform(patch("/api/v1/rooms/{roomId}", room.code())
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"2차 회식\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST_PARAMETER"));

        mockMvc.perform(delete("/api/v1/rooms/{roomId}", room.code())
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST_PARAMETER"));
    }

    @Test
    void 존재하지_않는_코드로_조회하면_404() throws Exception {
        mockMvc.perform(get("/api/v1/rooms/{code}", "23456789"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("ROOM_NOT_FOUND"));
    }

    @Test
    void 형식이_잘못된_코드로_조회하면_400() throws Exception {
        mockMvc.perform(get("/api/v1/rooms/{code}", "invalid-code"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_ROOM_CODE"));
    }

    @Test
    void 토큰_없이_조회하면_joined는_거짓이고_토큰을_보내면_참여_여부가_반영된다() throws Exception {
        String hostToken = 익명_인증("가현");
        String guestToken = 익명_인증("민수");
        생성된_방 room = 방_만들기(hostToken);
        long roomId = room.roomId();
        String code = room.code();

        mockMvc.perform(get("/api/v1/rooms/{code}", code)
                .header("Authorization", "Bearer " + guestToken))
            .andExpect(jsonPath("$.data.joined").value(false));

        입장(guestToken, roomId, "{}").andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/rooms/{code}", code)
                .header("Authorization", "Bearer " + guestToken))
            .andExpect(jsonPath("$.data.joined").value(true));

        mockMvc.perform(get("/api/v1/rooms/{code}", code))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.joined").value(false))
            .andExpect(jsonPath("$.data.name").value("우테코 회식"));
    }

    @Test
    void 방장이_이름과_업로드_권한과_만료_시간을_한꺼번에_수정한다() throws Exception {
        String token = 익명_인증("가현");
        생성된_방 room = 방_만들기(token);
        long roomId = room.roomId();

        수정(token, roomId, "{\"name\":\"2차 회식\",\"uploadPolicy\":\"host\",\"expiryHours\":72}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.roomId").value(roomId))
            .andExpect(jsonPath("$.data.name").value("2차 회식"))
            .andExpect(jsonPath("$.data.uploadPolicy").value("host"))
            .andExpect(jsonPath("$.data.hostName").value("가현"));

        mockMvc.perform(get("/api/v1/rooms/{code}", room.code()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.name").value("2차 회식"))
            .andExpect(jsonPath("$.data.uploadPolicy").value("host"));
    }

    @Test
    void 보낸_항목만_바뀌고_나머지는_그대로다() throws Exception {
        String token = 익명_인증("가현");
        생성된_방 room = 방_만들기(token);
        // 저장된 값과 비교해야 한다. DB는 마이크로초까지만 담아서, 생성 응답 값과는 정밀도가 다를 수 있다.
        String originalExpiresAt = 값(mockMvc.perform(get("/api/v1/rooms/{code}", room.code())).andReturn(),
            "expiresAt");

        수정(token, room.roomId(), "{\"name\":\"2차 회식\"}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.name").value("2차 회식"))
            .andExpect(jsonPath("$.data.uploadPolicy").value("everyone"))
            .andExpect(jsonPath("$.data.expiresAt").value(originalExpiresAt));
    }

    @Test
    void 만료_시간은_기존_만료_시각이_아니라_요청_시각_기준으로_다시_계산된다() throws Exception {
        String token = 익명_인증("가현");
        long roomId = 방_만들기(token).roomId();

        MvcResult patched = 수정(token, roomId, "{\"expiryHours\":72}")
            .andExpect(status().isOk())
            .andReturn();

        Instant expiresAt = Instant.parse(값(patched, "expiresAt"));
        assertThat(expiresAt).isBefore(Instant.now().plus(Duration.ofHours(73)));
        assertThat(expiresAt).isAfter(Instant.now().plus(Duration.ofHours(71)));
    }

    @Test
    void 아무_항목도_보내지_않으면_400_EMPTY_PATCH() throws Exception {
        String token = 익명_인증("가현");
        long roomId = 방_만들기(token).roomId();

        수정(token, roomId, "{}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("EMPTY_PATCH"));
    }

    @Test
    void 방장이_아니면_수정할_수_없다() throws Exception {
        String hostToken = 익명_인증("가현");
        String guestToken = 익명_인증("민수");
        long roomId = 방_만들기(hostToken).roomId();

        수정(guestToken, roomId, "{\"name\":\"2차 회식\"}")
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("NOT_ROOM_HOST"));
    }

    @Test
    void 허용되지_않은_만료_시간이면_400() throws Exception {
        String token = 익명_인증("가현");
        long roomId = 방_만들기(token).roomId();

        수정(token, roomId, "{\"expiryHours\":48}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_ROOM_EXPIRATION"));
    }

    @Test
    void 알_수_없는_업로드_권한이면_400() throws Exception {
        String token = 익명_인증("가현");
        long roomId = 방_만들기(token).roomId();

        수정(token, roomId, "{\"uploadPolicy\":\"nobody\"}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_UPLOAD_POLICY"));
    }

    @Test
    void 없는_방을_수정하면_404() throws Exception {
        수정(익명_인증("가현"), -1L, "{\"name\":\"2차 회식\"}")
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("ROOM_NOT_FOUND"));
    }

    @Test
    void 방장이_방을_삭제하면_삭제_시각과_영구_삭제_예정_시각을_받는다() throws Exception {
        String token = 익명_인증("가현");
        long roomId = 방_만들기(token).roomId();

        MvcResult deleted = 삭제(token, roomId)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.deletedAt").value(notNullValue()))
            .andExpect(jsonPath("$.data.purgeAt").value(notNullValue()))
            .andReturn();

        Instant deletedAt = Instant.parse(값(deleted, "deletedAt"));
        Instant purgeAt = Instant.parse(값(deleted, "purgeAt"));
        assertThat(purgeAt).isEqualTo(deletedAt.plus(Duration.ofDays(7)));
    }

    @Test
    void 삭제해도_행은_남고_상태만_DELETED가_된다() throws Exception {
        String token = 익명_인증("가현");
        생성된_방 room = 방_만들기(token);
        long roomId = room.roomId();
        String code = room.code();

        삭제(token, roomId).andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/rooms/{code}", code))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("DELETED"));
    }

    @Test
    void 이미_삭제된_방을_다시_삭제하면_410() throws Exception {
        String token = 익명_인증("가현");
        long roomId = 방_만들기(token).roomId();

        삭제(token, roomId).andExpect(status().isOk());

        삭제(token, roomId)
            .andExpect(status().isGone())
            .andExpect(jsonPath("$.code").value("ROOM_ALREADY_DELETED"));
    }

    @Test
    void 삭제된_방은_수정할_수_없다() throws Exception {
        String token = 익명_인증("가현");
        long roomId = 방_만들기(token).roomId();

        삭제(token, roomId).andExpect(status().isOk());

        수정(token, roomId, "{\"name\":\"2차 회식\"}")
            .andExpect(status().isGone())
            .andExpect(jsonPath("$.code").value("ROOM_EXPIRED"));
    }

    @Test
    void 방장이_아니면_삭제할_수_없다() throws Exception {
        String hostToken = 익명_인증("가현");
        String guestToken = 익명_인증("민수");
        long roomId = 방_만들기(hostToken).roomId();

        삭제(guestToken, roomId)
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("NOT_ROOM_HOST"));
    }

    @Test
    void 처음_입장하면_201_다시_입장하면_200이고_표시_이름은_인증_정보에서_온다() throws Exception {
        String hostToken = 익명_인증("가현");
        String guestToken = 익명_인증("민수");
        long roomId = 방_만들기(hostToken).roomId();

        MvcResult first = 입장(guestToken, roomId, "{}")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.roomId").value(roomId))
            .andExpect(jsonPath("$.data.userId").value(notNullValue()))
            .andExpect(jsonPath("$.data.displayName").value("민수"))
            .andExpect(jsonPath("$.data.hostId").value(notNullValue()))
            .andExpect(jsonPath("$.data.joinedAt").value(notNullValue()))
            .andReturn();

        MvcResult second = 입장(guestToken, roomId, "{}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.displayName").value("민수"))
            .andReturn();

        assertThat(값(second, "joinedAt")).isEqualTo(값(first, "joinedAt"));
    }

    @Test
    void 입장_응답의_hostId로_클라이언트가_방장_여부를_판단한다() throws Exception {
        String hostToken = 익명_인증("가현");
        String guestToken = 익명_인증("민수");
        long roomId = 방_만들기(hostToken).roomId();

        MvcResult hostJoin = 입장(hostToken, roomId, "{}").andExpect(status().isCreated()).andReturn();
        MvcResult guestJoin = 입장(guestToken, roomId, "{}").andExpect(status().isCreated()).andReturn();

        assertThat(값(hostJoin, "userId")).isEqualTo(값(hostJoin, "hostId"));
        assertThat(값(guestJoin, "userId")).isNotEqualTo(값(guestJoin, "hostId"));
    }

    @Test
    void 암호가_걸린_방은_암호를_보내야_들어갈_수_있다() throws Exception {
        String hostToken = 익명_인증("가현");
        String guestToken = 익명_인증("민수");
        MvcResult created = 방_생성(hostToken, "{\"name\":\"우테코 회식\",\"entryPassword\":\"sssok2026\"}")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.requiresPasscode").value(true))
            .andReturn();
        long roomId = Long.parseLong(값(created, "roomId"));

        입장(guestToken, roomId, "{}")
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("PASSCODE_REQUIRED"));

        입장(guestToken, roomId, "{\"passcode\":\"wrong-one\"}")
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("INVALID_PASSCODE"));

        입장(guestToken, roomId, "{\"passcode\":\"sssok2026\"}")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.displayName").value("민수"));
    }

    @Test
    void 없는_방에_입장하면_404() throws Exception {
        입장(익명_인증("민수"), -1L, "{}")
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("ROOM_NOT_FOUND"));
    }

    @Test
    void 삭제된_방에_입장하면_410() throws Exception {
        String hostToken = 익명_인증("가현");
        String guestToken = 익명_인증("민수");
        long roomId = 방_만들기(hostToken).roomId();

        삭제(hostToken, roomId).andExpect(status().isOk());

        입장(guestToken, roomId, "{}")
            .andExpect(status().isGone())
            .andExpect(jsonPath("$.code").value("ROOM_EXPIRED"));
    }

    @Test
    void 인증_없이_입장하면_401() throws Exception {
        long roomId = 방_만들기(익명_인증("가현")).roomId();

        mockMvc.perform(post("/api/v1/rooms/{roomId}/members", roomId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    private ResultActions 방_생성(String token, String body) throws Exception {
        return mockMvc.perform(post("/api/v1/rooms")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
    }

    private ResultActions 수정(String token, long roomId, String body) throws Exception {
        return mockMvc.perform(patch("/api/v1/rooms/{roomId}", roomId)
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
    }

    private ResultActions 삭제(String token, long roomId) throws Exception {
        return mockMvc.perform(delete("/api/v1/rooms/{roomId}", roomId)
            .header("Authorization", "Bearer " + token));
    }

    private ResultActions 입장(String token, long roomId, String body) throws Exception {
        return mockMvc.perform(post("/api/v1/rooms/{roomId}/members", roomId)
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
    }

    // roomId 는 수정·삭제·입장에, code 는 조회에 쓴다.
    private 생성된_방 방_만들기(String token) throws Exception {
        MvcResult created = 방_생성(token, "{\"name\":\"우테코 회식\"}").andReturn();
        return new 생성된_방(Long.parseLong(값(created, "roomId")), 값(created, "code"));
    }

    private record 생성된_방(long roomId, String code) {
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