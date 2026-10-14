package com.sssok.presentation.api.medialike;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sssok.application.port.out.FileStoragePort;
import com.sssok.support.AcceptanceTest;
import com.sssok.support.PostgresContainerSupport;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

// MediaDeleteApiTest 와 같은 설정이라야 테스트 컨텍스트를 재사용해 DB 연결 수가 모자라지 않는다.
@AcceptanceTest
@SpringBootTest(properties = "storage.cleanup.auto-purge=false")
class MediaLikeApiTest extends PostgresContainerSupport {

    private static final AtomicLong MEDIA_ID_SEQUENCE = new AtomicLong(System.currentTimeMillis());

    @Autowired
    WebApplicationContext context;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @MockitoBean
    FileStoragePort fileStoragePort;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void 좋아요를_누르면_200과_변경_후_상태를_받는다() throws Exception {
        String token = 익명_인증("가현");
        long roomId = 방_만들고_입장(token);
        long mediaId = 존재하는_미디어(roomId, "READY");

        좋아요(token, roomId, mediaId)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.mediaId").value(mediaId))
            .andExpect(jsonPath("$.data.liked").value(true))
            .andExpect(jsonPath("$.data.likeCount").value(1));
    }

    @Test
    void 이미_누른_사진에_다시_눌러도_200이고_개수와_이벤트가_늘지_않는다() throws Exception {
        String token = 익명_인증("가현");
        long roomId = 방_만들고_입장(token);
        long mediaId = 존재하는_미디어(roomId, "READY");
        좋아요(token, roomId, mediaId).andExpect(status().isOk());

        좋아요(token, roomId, mediaId)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.liked").value(true))
            .andExpect(jsonPath("$.data.likeCount").value(1));
        assertThat(좋아요_이벤트(roomId)).hasSize(1);
    }

    @Test
    void 취소하면_200과_liked_false를_받고_누르지_않은_사진에도_200이다() throws Exception {
        String token = 익명_인증("가현");
        long roomId = 방_만들고_입장(token);
        long mediaId = 존재하는_미디어(roomId, "READY");
        좋아요(token, roomId, mediaId).andExpect(status().isOk());

        취소(token, roomId, mediaId)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.mediaId").value(mediaId))
            .andExpect(jsonPath("$.data.liked").value(false))
            .andExpect(jsonPath("$.data.likeCount").value(0));
        취소(token, roomId, mediaId)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.liked").value(false))
            .andExpect(jsonPath("$.data.likeCount").value(0));
        assertThat(좋아요_이벤트(roomId)).hasSize(2);
    }

    @Test
    void 상태가_바뀌면_커밋_뒤_media_likes_updated가_likedByMe_없이_기록된다() throws Exception {
        String token = 익명_인증("가현");
        long roomId = 방_만들고_입장(token);
        long mediaId = 존재하는_미디어(roomId, "READY");

        좋아요(token, roomId, mediaId).andExpect(status().isOk());

        List<JsonNode> events = 좋아요_이벤트(roomId);
        assertThat(events).hasSize(1);
        JsonNode payload = events.get(0);
        assertThat(payload.get("roomId").asLong()).isEqualTo(roomId);
        assertThat(payload.get("mediaId").asLong()).isEqualTo(mediaId);
        assertThat(payload.get("likeCount").asLong()).isEqualTo(1);
        assertThat(payload.has("likedByMe")).isFalse();
    }

    @Test
    void 미디어_응답에_전체_좋아요_수와_보는_사람_기준_likedByMe가_실린다() throws Exception {
        String hostToken = 익명_인증("가현");
        String guestToken = 익명_인증("민수");
        long roomId = 방_만들고_입장(hostToken);
        입장(guestToken, roomId);
        long mediaId = 존재하는_미디어(roomId, "READY");
        좋아요(hostToken, roomId, mediaId).andExpect(status().isOk());

        조회(hostToken, "/api/v1/rooms/{roomId}/media", roomId)
            .andExpect(jsonPath("$.data.items[0].likeCount").value(1))
            .andExpect(jsonPath("$.data.items[0].likedByMe").value(true));
        조회(hostToken, "/api/v1/rooms/{roomId}/media/all", roomId)
            .andExpect(jsonPath("$.data.items[0].likeCount").value(1))
            .andExpect(jsonPath("$.data.items[0].likedByMe").value(true));
        조회(hostToken, "/api/v1/rooms/{roomId}/media/" + mediaId, roomId)
            .andExpect(jsonPath("$.data.likeCount").value(1))
            .andExpect(jsonPath("$.data.likedByMe").value(true));

        조회(guestToken, "/api/v1/rooms/{roomId}/media/all", roomId)
            .andExpect(jsonPath("$.data.items[0].likeCount").value(1))
            .andExpect(jsonPath("$.data.items[0].likedByMe").value(false));
        조회(guestToken, "/api/v1/rooms/{roomId}/media/" + mediaId, roomId)
            .andExpect(jsonPath("$.data.likedByMe").value(false));
    }

    @Test
    void 아무도_누르지_않은_미디어는_0과_false로_내려간다() throws Exception {
        String token = 익명_인증("가현");
        long roomId = 방_만들고_입장(token);
        존재하는_미디어(roomId, "READY");

        조회(token, "/api/v1/rooms/{roomId}/media", roomId)
            .andExpect(jsonPath("$.data.items[0].likeCount").value(0))
            .andExpect(jsonPath("$.data.items[0].likedByMe").value(false));
    }

    @Test
    void 미디어를_삭제하면_그_미디어의_좋아요도_함께_지워진다() throws Exception {
        String token = 익명_인증("가현");
        long roomId = 방_만들고_입장(token);
        long mediaId = 존재하는_미디어(roomId, "READY");
        좋아요(token, roomId, mediaId).andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/rooms/{roomId}/media/{mediaId}", roomId, mediaId)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());

        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM media_like WHERE media_id = ?", Integer.class, mediaId)).isZero();
    }

    @Test
    void 입장하지_않은_사용자면_403() throws Exception {
        String hostToken = 익명_인증("가현");
        String outsiderToken = 익명_인증("민수");
        long roomId = 방_만들고_입장(hostToken);
        long mediaId = 존재하는_미디어(roomId, "READY");

        좋아요(outsiderToken, roomId, mediaId)
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("NOT_ROOM_MEMBER"));
    }

    @Test
    void 없는_미디어_다른_방_미디어_RESERVED_FAILED_미디어면_404() throws Exception {
        String token = 익명_인증("가현");
        long roomId = 방_만들고_입장(token);
        long otherRoomId = 방_만들고_입장(token);
        long otherRoomMedia = 존재하는_미디어(otherRoomId, "READY");
        long reserved = 존재하는_미디어(roomId, "RESERVED");
        long failed = 존재하는_미디어(roomId, "FAILED");

        for (long mediaId : List.of(MEDIA_ID_SEQUENCE.incrementAndGet(), otherRoomMedia, reserved, failed)) {
            좋아요(token, roomId, mediaId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEDIA_NOT_FOUND"));
            취소(token, roomId, mediaId)
                .andExpect(status().isNotFound());
        }
    }

    @Test
    void 삭제된_방이면_410() throws Exception {
        String token = 익명_인증("가현");
        long roomId = 방_만들고_입장(token);
        long mediaId = 존재하는_미디어(roomId, "READY");
        mockMvc.perform(delete("/api/v1/rooms/{roomId}", roomId)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());

        좋아요(token, roomId, mediaId)
            .andExpect(status().isGone())
            .andExpect(jsonPath("$.code").value("ROOM_EXPIRED"));
    }

    private ResultActions 좋아요(String token, long roomId, long mediaId) throws Exception {
        return mockMvc.perform(put("/api/v1/rooms/{roomId}/media/{mediaId}/likes", roomId, mediaId)
            .header("Authorization", "Bearer " + token));
    }

    private ResultActions 취소(String token, long roomId, long mediaId) throws Exception {
        return mockMvc.perform(delete("/api/v1/rooms/{roomId}/media/{mediaId}/likes", roomId, mediaId)
            .header("Authorization", "Bearer " + token));
    }

    private ResultActions 조회(String token, String path, long roomId) throws Exception {
        return mockMvc.perform(get(path, roomId).header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());
    }

    private List<JsonNode> 좋아요_이벤트(long roomId) throws Exception {
        List<String> payloads = jdbcTemplate.queryForList(
            "SELECT payload FROM room_events WHERE room_id = ? AND event_type = 'media.likes.updated' ORDER BY id",
            String.class, roomId);
        List<JsonNode> nodes = new ArrayList<>();
        for (String payload : payloads) {
            nodes.add(objectMapper.readTree(payload));
        }
        return nodes;
    }

    private long 존재하는_미디어(long roomId, String status) {
        long mediaId = MEDIA_ID_SEQUENCE.incrementAndGet();
        jdbcTemplate.update("""
            INSERT INTO stored_file
                (id, room_id, uploader_id, original_file_name, media_type, file_size_bytes,
                 storage_key, status, created_at, updated_at, reserved_at, retry_count)
            VALUES (?, ?, 1, 'test.jpg', 'JPEG', 1024, ?, ?, now(), now(), now(), 0)
            """, mediaId, roomId, "test-key-" + mediaId, status);
        return mediaId;
    }

    private long 방_만들고_입장(String token) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/rooms")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"우테코 회식\"}"))
            .andReturn();
        long roomId = Long.parseLong(값(created, "roomId"));
        입장(token, roomId);
        return roomId;
    }

    private void 입장(String token, long roomId) throws Exception {
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
