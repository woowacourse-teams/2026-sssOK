package com.sssok.presentation.api.folder;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sssok.infrastructure.persistence.folder.FolderMediaJpaEntity;
import com.sssok.infrastructure.persistence.folder.FolderMediaJpaRepository;
import com.sssok.support.PostgresContainerSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

// API 인수 테스트 — 방 생성/입장부터 폴더 생성까지 실제 PostgreSQL 위에서 관통 확인한다.
@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureMockMvc
class FolderApiTest extends PostgresContainerSupport {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    FolderMediaJpaRepository folderMediaJpaRepository;

    @Test
    void 입장한_사용자가_폴더를_생성하면_201과_폴더_정보를_받는다() throws Exception {
        String token = 익명_인증("가현");
        long roomId = 방_만들고_입장(token);

        폴더_생성(token, roomId, "{\"name\":\"맛집\"}")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.id").value(org.hamcrest.Matchers.notNullValue()))
            .andExpect(jsonPath("$.data.name").value("맛집"))
            .andExpect(jsonPath("$.data.createdAt").value(org.hamcrest.Matchers.notNullValue()))
            .andExpect(jsonPath("$.data.photoCount").value(0));
    }

    @Test
    void 같은_방에_같은_이름의_폴더를_또_만들면_409() throws Exception {
        String token = 익명_인증("가현");
        long roomId = 방_만들고_입장(token);
        폴더_생성(token, roomId, "{\"name\":\"맛집\"}").andExpect(status().isCreated());

        폴더_생성(token, roomId, "{\"name\":\"맛집\"}")
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("DUPLICATE_FOLDER_NAME"));
    }

    @Test
    void 입장하지_않은_사용자가_폴더를_생성하면_403() throws Exception {
        String hostToken = 익명_인증("가현");
        String guestToken = 익명_인증("민수");
        long roomId = 방_만들고_입장(hostToken);

        폴더_생성(guestToken, roomId, "{\"name\":\"맛집\"}")
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("NOT_ROOM_MEMBER"));
    }

    @Test
    void 없는_방에_폴더를_생성하면_404() throws Exception {
        String token = 익명_인증("가현");

        폴더_생성(token, -1L, "{\"name\":\"맛집\"}")
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("ROOM_NOT_FOUND"));
    }

    @Test
    void 인증_없이_폴더를_생성하면_401() throws Exception {
        String token = 익명_인증("가현");
        long roomId = 방_만들고_입장(token);

        mockMvc.perform(post("/api/v1/rooms/{roomId}/folders", roomId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"맛집\"}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void 이름_없이_생성하면_400() throws Exception {
        String token = 익명_인증("가현");
        long roomId = 방_만들고_입장(token);

        폴더_생성(token, roomId, "{\"name\":\"\"}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_FOLDER_NAME"));
    }

    @Test
    void 이름이_12자를_넘으면_400() throws Exception {
        String token = 익명_인증("가현");
        long roomId = 방_만들고_입장(token);

        폴더_생성(token, roomId, "{\"name\":\"" + "가".repeat(13) + "\"}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("FOLDER_NAME_TOO_LONG"));
    }

    @Test
    void 폴더_이름을_바꾸면_200과_바뀐_이름을_받는다() throws Exception {
        String token = 익명_인증("가현");
        long roomId = 방_만들고_입장(token);
        long folderId = 폴더_만들기(token, roomId, "맛집");

        폴더_이름변경(token, roomId, folderId, "{\"name\":\"카페\"}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value(folderId))
            .andExpect(jsonPath("$.data.name").value("카페"));
    }

    @Test
    void 자기_자신과_같은_이름으로_바꾸면_중복이_아니라_200이다() throws Exception {
        String token = 익명_인증("가현");
        long roomId = 방_만들고_입장(token);
        long folderId = 폴더_만들기(token, roomId, "맛집");

        폴더_이름변경(token, roomId, folderId, "{\"name\":\"맛집\"}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.name").value("맛집"));
    }

    @Test
    void 다른_폴더가_쓰는_이름으로_바꾸면_409() throws Exception {
        String token = 익명_인증("가현");
        long roomId = 방_만들고_입장(token);
        폴더_만들기(token, roomId, "카페");
        long folderId = 폴더_만들기(token, roomId, "맛집");

        폴더_이름변경(token, roomId, folderId, "{\"name\":\"카페\"}")
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("DUPLICATE_FOLDER_NAME"));
    }

    @Test
    void 없는_폴더를_바꾸면_404() throws Exception {
        String token = 익명_인증("가현");
        long roomId = 방_만들고_입장(token);

        폴더_이름변경(token, roomId, -1L, "{\"name\":\"카페\"}")
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("FOLDER_NOT_FOUND"));
    }

    @Test
    void 폴더를_삭제하면_200과_삭제된_폴더_id를_받는다() throws Exception {
        String token = 익명_인증("가현");
        long roomId = 방_만들고_입장(token);
        long folderId = 폴더_만들기(token, roomId, "맛집");

        폴더_삭제(token, roomId, folderId)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.deletedFolderId").value(folderId))
            .andExpect(jsonPath("$.data.detachedPhotoCount").value(0));
    }

    @Test
    void 삭제된_폴더_이름으로_다시_생성할_수_있다() throws Exception {
        String token = 익명_인증("가현");
        long roomId = 방_만들고_입장(token);
        long folderId = 폴더_만들기(token, roomId, "맛집");
        폴더_삭제(token, roomId, folderId).andExpect(status().isOk());

        폴더_생성(token, roomId, "{\"name\":\"맛집\"}")
            .andExpect(status().isCreated());
    }

    @Test
    void 담겨있던_미디어와의_관계만_끊고_개수를_반환한다() throws Exception {
        String token = 익명_인증("가현");
        long roomId = 방_만들고_입장(token);
        long folderId = 폴더_만들기(token, roomId, "맛집");
        folderMediaJpaRepository.save(new FolderMediaJpaEntity(null, folderId, 1L));
        folderMediaJpaRepository.save(new FolderMediaJpaEntity(null, folderId, 2L));

        폴더_삭제(token, roomId, folderId)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.detachedPhotoCount").value(2));
    }

    @Test
    void 없는_폴더를_삭제하면_404() throws Exception {
        String token = 익명_인증("가현");
        long roomId = 방_만들고_입장(token);

        폴더_삭제(token, roomId, -1L)
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("FOLDER_NOT_FOUND"));
    }

    @Test
    void 입장하지_않은_사용자가_폴더를_삭제하면_403() throws Exception {
        String hostToken = 익명_인증("가현");
        String guestToken = 익명_인증("민수");
        long roomId = 방_만들고_입장(hostToken);
        long folderId = 폴더_만들기(hostToken, roomId, "맛집");

        폴더_삭제(guestToken, roomId, folderId)
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("NOT_ROOM_MEMBER"));
    }

    private ResultActions 폴더_생성(String token, long roomId, String body) throws Exception {
        return mockMvc.perform(post("/api/v1/rooms/{roomId}/folders", roomId)
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
    }

    private ResultActions 폴더_이름변경(String token, long roomId, long folderId, String body) throws Exception {
        return mockMvc.perform(patch("/api/v1/rooms/{roomId}/folders/{folderId}", roomId, folderId)
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
    }

    private ResultActions 폴더_삭제(String token, long roomId, long folderId) throws Exception {
        return mockMvc.perform(delete("/api/v1/rooms/{roomId}/folders/{folderId}", roomId, folderId)
            .header("Authorization", "Bearer " + token));
    }

    private long 폴더_만들기(String token, long roomId, String name) throws Exception {
        MvcResult created = 폴더_생성(token, roomId, "{\"name\":\"" + name + "\"}").andReturn();
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
