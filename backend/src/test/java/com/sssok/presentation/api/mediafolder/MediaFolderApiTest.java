package com.sssok.presentation.api.mediafolder;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sssok.support.PostgresContainerSupport;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

// API 인수 테스트 — 방/폴더/미디어를 준비하고 실제 PostgreSQL 위에서 담기·꺼내기를 관통 확인한다.
@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureMockMvc
class MediaFolderApiTest extends PostgresContainerSupport {

    // 테스트끼리 롤백 없이 같은 컨테이너를 공유하므로, 미디어 id/storage_key가 겹치지 않게 매번 새로 발급한다.
    private static final AtomicLong MEDIA_ID_SEQUENCE = new AtomicLong(System.currentTimeMillis());

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void 미디어_여러개를_폴더_하나에_담으면_200과_결과를_받는다() throws Exception {
        String token = 익명_인증("가현");
        long roomId = 방_만들고_입장(token);
        long folderId = 폴더_만들기(token, roomId, "맛집");
        long media1 = 존재하는_미디어();
        long media2 = 존재하는_미디어();

        담기(token, roomId, "{\"mediaIds\":[%d,%d],\"folderId\":%d}".formatted(media1, media2, folderId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.updatedCount").value(2))
            .andExpect(jsonPath("$.data.alreadyInCount").value(0))
            .andExpect(jsonPath("$.data.notFoundMediaIds").isEmpty())
            .andExpect(jsonPath("$.data.folder.id").value(folderId))
            .andExpect(jsonPath("$.data.folder.photoCount").value(2));
    }

    @Test
    void 같은_미디어를_다시_담아도_오류가_아니고_alreadyInCount로_집계된다() throws Exception {
        String token = 익명_인증("가현");
        long roomId = 방_만들고_입장(token);
        long folderId = 폴더_만들기(token, roomId, "맛집");
        long mediaId = 존재하는_미디어();
        담기(token, roomId, "{\"mediaIds\":[%d],\"folderId\":%d}".formatted(mediaId, folderId))
            .andExpect(status().isOk());

        담기(token, roomId, "{\"mediaIds\":[%d],\"folderId\":%d}".formatted(mediaId, folderId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.updatedCount").value(0))
            .andExpect(jsonPath("$.data.alreadyInCount").value(1));
    }

    @Test
    void 존재하지_않는_미디어는_건너뛰고_notFoundMediaIds로_알려준다() throws Exception {
        String token = 익명_인증("가현");
        long roomId = 방_만들고_입장(token);
        long folderId = 폴더_만들기(token, roomId, "맛집");
        long mediaId = 존재하는_미디어();
        long notFoundMediaId = MEDIA_ID_SEQUENCE.incrementAndGet();

        담기(token, roomId, "{\"mediaIds\":[%d,%d],\"folderId\":%d}".formatted(mediaId, notFoundMediaId, folderId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.updatedCount").value(1))
            .andExpect(jsonPath("$.data.notFoundMediaIds[0]").value(notFoundMediaId));
    }

    @Test
    void 없는_폴더면_404() throws Exception {
        String token = 익명_인증("가현");
        long roomId = 방_만들고_입장(token);
        long mediaId = 존재하는_미디어();

        담기(token, roomId, "{\"mediaIds\":[%d],\"folderId\":-1}".formatted(mediaId))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("FOLDER_NOT_FOUND"));
    }

    @Test
    void mediaIds가_비어있으면_400과_INVALID_PARAM() throws Exception {
        String token = 익명_인증("가현");
        long roomId = 방_만들고_입장(token);
        long folderId = 폴더_만들기(token, roomId, "맛집");

        담기(token, roomId, "{\"mediaIds\":[],\"folderId\":%d}".formatted(folderId))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_PARAM"));
    }

    @Test
    void folderId가_없으면_400과_INVALID_PARAM() throws Exception {
        String token = 익명_인증("가현");
        long roomId = 방_만들고_입장(token);
        long mediaId = 존재하는_미디어();

        담기(token, roomId, "{\"mediaIds\":[%d]}".formatted(mediaId))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_PARAM"));
    }

    @Test
    void 입장하지_않은_사용자가_담으면_403() throws Exception {
        String hostToken = 익명_인증("가현");
        String guestToken = 익명_인증("민수");
        long roomId = 방_만들고_입장(hostToken);
        long folderId = 폴더_만들기(hostToken, roomId, "맛집");
        long mediaId = 존재하는_미디어();

        담기(guestToken, roomId, "{\"mediaIds\":[%d],\"folderId\":%d}".formatted(mediaId, folderId))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("NOT_ROOM_MEMBER"));
    }

    @Test
    void 지정한_폴더에서만_꺼내면_다른_폴더에_남아있어_루트로_가지_않는다() throws Exception {
        String token = 익명_인증("가현");
        long roomId = 방_만들고_입장(token);
        long folderA = 폴더_만들기(token, roomId, "맛집");
        long folderB = 폴더_만들기(token, roomId, "카페");
        long mediaId = 존재하는_미디어();
        담기(token, roomId, "{\"mediaIds\":[%d],\"folderId\":%d}".formatted(mediaId, folderA)).andExpect(status().isOk());
        담기(token, roomId, "{\"mediaIds\":[%d],\"folderId\":%d}".formatted(mediaId, folderB)).andExpect(status().isOk());

        꺼내기(token, roomId, "{\"mediaIds\":[%d],\"folderIds\":[%d]}".formatted(mediaId, folderA))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.updatedCount").value(1))
            .andExpect(jsonPath("$.data.movedToRootMediaIds").isEmpty())
            .andExpect(jsonPath("$.data.folders[0].id").value(folderA));
    }

    @Test
    void 마지막_폴더에서_꺼내면_movedToRootMediaIds에_담긴다() throws Exception {
        String token = 익명_인증("가현");
        long roomId = 방_만들고_입장(token);
        long folderId = 폴더_만들기(token, roomId, "맛집");
        long mediaId = 존재하는_미디어();
        담기(token, roomId, "{\"mediaIds\":[%d],\"folderId\":%d}".formatted(mediaId, folderId))
            .andExpect(status().isOk());

        꺼내기(token, roomId, "{\"mediaIds\":[%d],\"folderIds\":[%d]}".formatted(mediaId, folderId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.movedToRootMediaIds[0]").value(mediaId));
    }

    @Test
    void folderIds를_생략하면_속한_모든_폴더에서_꺼낸다() throws Exception {
        String token = 익명_인증("가현");
        long roomId = 방_만들고_입장(token);
        long folderA = 폴더_만들기(token, roomId, "맛집");
        long folderB = 폴더_만들기(token, roomId, "카페");
        long mediaId = 존재하는_미디어();
        담기(token, roomId, "{\"mediaIds\":[%d],\"folderId\":%d}".formatted(mediaId, folderA)).andExpect(status().isOk());
        담기(token, roomId, "{\"mediaIds\":[%d],\"folderId\":%d}".formatted(mediaId, folderB)).andExpect(status().isOk());

        꺼내기(token, roomId, "{\"mediaIds\":[%d]}".formatted(mediaId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.updatedCount").value(2))
            .andExpect(jsonPath("$.data.movedToRootMediaIds[0]").value(mediaId))
            .andExpect(jsonPath("$.data.folders", org.hamcrest.Matchers.hasSize(2)));
    }

    @Test
    void 꺼내기에서_존재하지_않는_미디어는_notFoundMediaIds로_알려준다() throws Exception {
        String token = 익명_인증("가현");
        long roomId = 방_만들고_입장(token);
        long folderId = 폴더_만들기(token, roomId, "맛집");
        long mediaId = 존재하는_미디어();
        long notFoundMediaId = MEDIA_ID_SEQUENCE.incrementAndGet();
        담기(token, roomId, "{\"mediaIds\":[%d],\"folderId\":%d}".formatted(mediaId, folderId))
            .andExpect(status().isOk());

        꺼내기(token, roomId, "{\"mediaIds\":[%d,%d],\"folderIds\":[%d]}".formatted(mediaId, notFoundMediaId, folderId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.notFoundMediaIds[0]").value(notFoundMediaId));
    }

    @Test
    void 꺼내기에서_없는_폴더가_있으면_404() throws Exception {
        String token = 익명_인증("가현");
        long roomId = 방_만들고_입장(token);
        long mediaId = 존재하는_미디어();

        꺼내기(token, roomId, "{\"mediaIds\":[%d],\"folderIds\":[-1]}".formatted(mediaId))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("FOLDER_NOT_FOUND"));
    }

    @Test
    void 꺼내기에서_mediaIds가_비어있으면_400과_INVALID_PARAM() throws Exception {
        String token = 익명_인증("가현");
        long roomId = 방_만들고_입장(token);

        꺼내기(token, roomId, "{\"mediaIds\":[]}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_PARAM"));
    }

    @Test
    void 입장하지_않은_사용자가_꺼내면_403() throws Exception {
        String hostToken = 익명_인증("가현");
        String guestToken = 익명_인증("민수");
        long roomId = 방_만들고_입장(hostToken);
        long folderId = 폴더_만들기(hostToken, roomId, "맛집");
        long mediaId = 존재하는_미디어();

        꺼내기(guestToken, roomId, "{\"mediaIds\":[%d],\"folderIds\":[%d]}".formatted(mediaId, folderId))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("NOT_ROOM_MEMBER"));
    }

    private ResultActions 꺼내기(String token, long roomId, String body) throws Exception {
        return mockMvc.perform(delete("/api/v1/rooms/{roomId}/media/folders", roomId)
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
    }

    private long 존재하는_미디어() {
        long mediaId = MEDIA_ID_SEQUENCE.incrementAndGet();
        jdbcTemplate.update("""
            INSERT INTO stored_file
                (id, room_id, uploader_id, original_file_name, media_type, file_size_bytes, storage_key, status, created_at)
            VALUES (?, 1, 1, 'test.jpg', 'JPEG', 1024, ?, 'COMPLETED', now())
            """, mediaId, "test-key-" + mediaId);
        return mediaId;
    }

    private ResultActions 담기(String token, long roomId, String body) throws Exception {
        return mockMvc.perform(put("/api/v1/rooms/{roomId}/media/folders", roomId)
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
    }

    private long 폴더_만들기(String token, long roomId, String name) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/rooms/{roomId}/folders", roomId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + name + "\"}"))
            .andReturn();
        return Long.parseLong(값(created, "id"));
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
