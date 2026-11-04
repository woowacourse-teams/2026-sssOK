package com.sssok.renewal.incoming.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sssok.renewal.outgoing.storage.FileStorage;
import com.sssok.support.AcceptanceTest;
import com.sssok.support.PostgresContainerSupport;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

// #670 사진 신고. MediaLikeApiTest 와 같은 설정이라야 테스트 컨텍스트를 재사용한다.
@AcceptanceTest
@SpringBootTest(properties = "storage.cleanup.auto-purge=false")
class MediaReportApiTest extends PostgresContainerSupport {

    private static final AtomicLong MEDIA_ID_SEQUENCE = new AtomicLong(System.currentTimeMillis() + 1_000_000);

    @Autowired
    WebApplicationContext context;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @MockitoBean
    FileStorage fileStorage;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void 사진을_신고하면_201과_함께_대상_사진_사유_접수_시각_신고_당시_닉네임과_방_이름을_저장한다() throws Exception {
        String token = 익명_인증("가현");
        long roomId = 방_만들고_입장(token);
        long mediaId = 존재하는_미디어(roomId, "READY");

        신고(token, roomId, mediaId, "{\"reason\":\"PRIVACY_VIOLATION\"}")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.mediaId").value(mediaId))
            .andExpect(jsonPath("$.data.reason").value("PRIVACY_VIOLATION"))
            .andExpect(jsonPath("$.data.reportedAt").isNotEmpty());

        Map<String, Object> saved = jdbcTemplate.queryForMap(
            "SELECT room_id, room_name, nickname, reason, reported_at FROM media_report WHERE media_id = ?", mediaId);
        assertThat(saved.get("room_id")).isEqualTo(roomId);
        assertThat(saved.get("room_name")).isEqualTo("우테코 회식");
        assertThat(saved.get("nickname")).isEqualTo("가현");
        assertThat(saved.get("reason")).isEqualTo("PRIVACY_VIOLATION");
        assertThat(saved.get("reported_at")).isNotNull();
    }

    @Test
    void 같은_사진을_다시_신고하면_409이고_한_건만_남는다() throws Exception {
        String token = 익명_인증("가현");
        long roomId = 방_만들고_입장(token);
        long mediaId = 존재하는_미디어(roomId, "READY");
        신고(token, roomId, mediaId, "{\"reason\":\"HARASSMENT\"}").andExpect(status().isCreated());

        신고(token, roomId, mediaId, "{\"reason\":\"COPYRIGHT_VIOLATION\"}")
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("MEDIA_ALREADY_REPORTED"));

        assertThat(신고_수(mediaId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT reason FROM media_report WHERE media_id = ?", String.class, mediaId)).isEqualTo("HARASSMENT");
    }

    @Test
    void 다른_사람은_같은_사진을_따로_신고할_수_있다() throws Exception {
        String hostToken = 익명_인증("가현");
        long roomId = 방_만들고_입장(hostToken);
        String guestToken = 익명_인증("민수");
        입장(guestToken, roomId);
        long mediaId = 존재하는_미디어(roomId, "READY");

        신고(hostToken, roomId, mediaId, "{\"reason\":\"HARASSMENT\"}").andExpect(status().isCreated());
        신고(guestToken, roomId, mediaId, "{\"reason\":\"INAPPROPRIATE_CONTENT\"}").andExpect(status().isCreated());

        assertThat(신고_수(mediaId)).isEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"reason\":\"\"}", "{\"reason\":\"SPAM\"}", "{\"reason\":\"harassment\"}"})
    void 사유가_없거나_지원하지_않는_값이면_400이고_저장하지_않는다(String body) throws Exception {
        String token = 익명_인증("가현");
        long roomId = 방_만들고_입장(token);
        long mediaId = 존재하는_미디어(roomId, "READY");

        신고(token, roomId, mediaId, body).andExpect(status().isBadRequest());

        assertThat(신고_수(mediaId)).isZero();
    }

    @Test
    void 없는_사진을_신고하면_404다() throws Exception {
        String token = 익명_인증("가현");
        long roomId = 방_만들고_입장(token);

        신고(token, roomId, MEDIA_ID_SEQUENCE.incrementAndGet(), "{\"reason\":\"HARASSMENT\"}")
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("MEDIA_NOT_FOUND"));
    }

    @Test
    void 다른_방_사진을_신고하면_404다() throws Exception {
        String token = 익명_인증("가현");
        long roomId = 방_만들고_입장(token);
        long otherRoomId = 방_만들고_입장(익명_인증("민수"));
        long otherRoomMedia = 존재하는_미디어(otherRoomId, "READY");

        신고(token, roomId, otherRoomMedia, "{\"reason\":\"HARASSMENT\"}")
            .andExpect(status().isNotFound());

        assertThat(신고_수(otherRoomMedia)).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"RESERVED", "FAILED"})
    void 갤러리에_보이지_않는_사진을_신고하면_404다(String uploadStatus) throws Exception {
        String token = 익명_인증("가현");
        long roomId = 방_만들고_입장(token);
        long mediaId = 존재하는_미디어(roomId, uploadStatus);

        신고(token, roomId, mediaId, "{\"reason\":\"HARASSMENT\"}")
            .andExpect(status().isNotFound());
    }

    @Test
    void 입장하지_않은_사용자가_신고하면_403이다() throws Exception {
        long roomId = 방_만들고_입장(익명_인증("가현"));
        long mediaId = 존재하는_미디어(roomId, "READY");

        신고(익명_인증("철수"), roomId, mediaId, "{\"reason\":\"HARASSMENT\"}")
            .andExpect(status().isForbidden());

        assertThat(신고_수(mediaId)).isZero();
    }

    private ResultActions 신고(String token, long roomId, long mediaId, String body) throws Exception {
        return mockMvc.perform(post("/api/v1/rooms/{roomId}/media/{mediaId}/reports", roomId, mediaId)
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
    }

    private long 신고_수(long mediaId) {
        return jdbcTemplate.queryForObject(
            "SELECT count(*) FROM media_report WHERE media_id = ?", Long.class, mediaId);
    }

    private long 존재하는_미디어(long roomId, String status) {
        long mediaId = MEDIA_ID_SEQUENCE.incrementAndGet();
        jdbcTemplate.update("""
            INSERT INTO stored_file
                (id, room_id, uploader_id, original_file_name, media_type, file_size_bytes,
                 storage_key, status, created_at, updated_at, reserved_at, retry_count)
            VALUES (?, ?, 1, 'test.jpg', 'JPEG', 1024, ?, ?, now(), now(), now(), 0)
            """, mediaId, roomId, "report-test-key-" + mediaId, status);
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

    private void 입장(String token, long roomId) throws Exception {
        String code = jdbcTemplate.queryForObject("SELECT code FROM room WHERE id = ?", String.class, roomId);
        mockMvc.perform(post("/api/v1/rooms/{roomId}/members", roomId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + code + "\"}"))
            .andExpect(status().is2xxSuccessful());
    }

    private String 값(MvcResult result, String field) throws Exception {
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("data").get(field).asText();
    }

    private String 익명_인증(String nickname) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/anonymous").header("X-CSRF-Protection", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\":\"" + nickname + "\"}"))
            .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("data").get("accessToken").asText();
    }
}
