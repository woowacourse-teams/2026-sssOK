package com.sssok.support;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sssok.application.auth.AnonymousAuthService;
import com.sssok.application.auth.AuthResult;
import com.sssok.application.port.out.FileStoragePort;
import com.sssok.application.port.out.ImageDescriptionPort;
import com.sssok.application.port.out.MediaSearchDocumentRepository;
import com.sssok.application.port.out.MediaSearchDocumentRepository.AnalysisAttempt;
import com.sssok.application.port.out.TextEmbeddingPort;
import com.sssok.application.room.CreateRoomService;
import com.sssok.domain.search.ImageAnalysis;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@AcceptanceTest
@SpringBootTest(properties = {
    "media.search.enabled=true", "media.search.provider=test", "media.search.created-since=2026-01-01T00:00:00Z",
    "media.search.query.min-similarity=0", "media.search.sweep-delay=3600000",
    "media.thumbnail.auto-generate=false"
})
@AutoConfigureMockMvc
class ImageSearchApiTest extends PostgresContainerSupport {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired AnonymousAuthService auth;
    @Autowired CreateRoomService rooms;
    @Autowired com.sssok.application.folder.CreateFolderService folders;
    @Autowired MediaSearchDocumentRepository documents;
    @MockitoBean TextEmbeddingPort embeddings;
    @MockitoBean ImageDescriptionPort descriptions;
    @MockitoBean FileStoragePort storage;
    @MockitoBean(name = "imageAnalysisTaskExecutor") AsyncTaskExecutor executor;
    private AuthResult member;
    private Long roomId;

    @BeforeEach
    void setup() {
        cleanup();
        member = auth.authenticate("검색 사용자");
        roomId = rooms.create(member.userId(), "검색 방", null, null).room().getId();
        given(embeddings.embed(anyString(), any())).willReturn(
            new TextEmbeddingPort.Embedding(List.of(1.0, 0.0, 0.0), "test-model"));
        given(storage.presignGet(any(), anyString(), anyString(), any())).willReturn("https://signed.test/image");
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM folder_media WHERE media_id BETWEEN 460201 AND 460230");
        jdbc.update("DELETE FROM media_like WHERE media_id BETWEEN 460201 AND 460230");
        jdbc.update("DELETE FROM stored_file WHERE id BETWEEN 460201 AND 460230");
    }

    @Test
    void Swagger에_검색_응답과_오류_스키마를_제공한다() throws Exception {
        mvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.paths['/api/v1/rooms/{roomId}/media/all'].get.tags[0]")
                .value("미디어 조회"))
            .andExpect(jsonPath("$.paths['/api/v1/rooms/{roomId}/media/all'].get.responses['200']"
                + ".content['application/json'].schema").exists())
            .andExpect(jsonPath("$.paths['/api/v1/rooms/{roomId}/media/all'].get.responses['503']"
                + ".content['application/json'].schema['$ref']").value("#/components/schemas/ErrorResponse"))
            .andExpect(jsonPath("$.components.schemas.AllMediaListResponse.properties.items").exists());
    }

    @Test
    void 임계값_경계와_정렬을_적용하고_모델과_차원이_다르면_제외한다() throws Exception {
        insert(460201, roomId, List.of(1.0, 0.0, 0.0), "test-model");
        insert(460202, roomId, List.of(1.0, 0.0, 0.0), "test-model");
        insert(460203, roomId, List.of(0.0, 1.0, 0.0), "test-model");
        insert(460204, roomId, List.of(-1.0, 0.0, 0.0), "test-model");
        insert(460205, roomId, List.of(1.0, 0.0), "test-model");
        insert(460206, roomId, List.of(1.0, 0.0, 0.0), "other-model");
        search("  바닷가\t단체 사진  ").andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items.length()").value(3))
            .andExpect(jsonPath("$.data.items[0].mediaId").value(460201))
            .andExpect(jsonPath("$.data.items[1].mediaId").value(460202))
            .andExpect(jsonPath("$.data.items[2].mediaId").value(460203))
            .andExpect(jsonPath("$.data.items[0].displayUrl").value("https://signed.test/image"));
        verify(embeddings).embed(org.mockito.ArgumentMatchers.eq("바닷가 단체 사진"), any());
    }

    @Test
    void 검색_결과에도_요청자의_좋아요_상태를_반영한다() throws Exception {
        insert(460201, roomId, List.of(1.0, 0.0, 0.0), "test-model");
        insert(460202, roomId, List.of(1.0, 0.0, 0.0), "test-model");
        jdbc.update("""
            INSERT INTO media_like (media_id, member_id, created_at, updated_at)
            VALUES (?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """, 460201L, member.userId());
        search("사진").andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[0].likeCount").value(1))
            .andExpect(jsonPath("$.data.items[0].likedByMe").value(true))
            .andExpect(jsonPath("$.data.items[1].likeCount").value(0))
            .andExpect(jsonPath("$.data.items[1].likedByMe").value(false));
    }

    @Test
    void 조건에_맞는_이미지가_20개보다_많아도_모두_반환한다() throws Exception {
        for (long id = 460201; id <= 460225; id++) {
            insert(id, roomId, List.of(1.0, 0.0, 0.0), "test-model");
        }
        search("단체 사진").andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(25));
    }

    @Test
    void 다른_방과_미완료_문서와_삭제된_이미지는_제외한다() throws Exception {
        Long otherRoom = rooms.create(member.userId(), "다른 방", null, null).room().getId();
        insert(460201, otherRoom, List.of(1.0, 0.0, 0.0), "test-model");
        insert(460202, roomId, List.of(1.0, 0.0, 0.0), "test-model");
        insert(460203, roomId, List.of(1.0, 0.0, 0.0), "test-model");
        jdbc.update("UPDATE media_search_document SET status = 'PENDING' WHERE media_id = 460202");
        jdbc.update("DELETE FROM stored_file WHERE id = 460203");
        search("사진").andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(0));
    }

    @Test
    void 입장하지_않은_사용자는_검색할_수_없다() throws Exception {
        AuthResult stranger = auth.authenticate("방 밖 사용자");
        mvc.perform(get(path()).param("query", "사진").header("Authorization", "Bearer " + stranger.accessToken()))
            .andExpect(status().isForbidden());
        org.mockito.Mockito.verifyNoInteractions(embeddings);
    }

    @Test
    void 인증_없는_검색은_거절한다() throws Exception {
        mvc.perform(get(path()).param("query", "사진")).andExpect(status().isUnauthorized());
    }

    @Test
    void 빈_검색어와_길이_초과는_400이다() throws Exception {
        search(" \t ").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_SEARCH_QUERY"));
        search("가".repeat(201)).andExpect(status().isBadRequest());
        org.mockito.Mockito.verifyNoInteractions(embeddings);
    }

    @Test
    void 임베딩_장애는_빈_결과_대신_503이다() throws Exception {
        given(embeddings.embed(anyString(), any())).willThrow(new IllegalStateException("timeout"));
        search("사진").andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value("IMAGE_SEARCH_UNAVAILABLE"));
    }

    @Test
    void 잘못된_임베딩은_거리_계산_전에_거절한다() throws Exception {
        given(embeddings.embed(anyString(), any())).willReturn(
            new TextEmbeddingPort.Embedding(List.of(0.0, 0.0, 0.0), "test-model"));
        search("사진").andExpect(status().isServiceUnavailable());
    }

    @Test
    void 검색어_생략은_AI_호출_없이_기존_전체_목록을_반환한다() throws Exception {
        insert(460201, roomId, List.of(1.0, 0.0, 0.0), "test-model");
        jdbc.update("DELETE FROM media_search_document WHERE media_id = 460201");
        mvc.perform(get(path()).header("Authorization", "Bearer " + member.accessToken()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[0].mediaId").value(460201));
        org.mockito.Mockito.verifyNoInteractions(embeddings);
    }

    @Test
    void 검색에도_폴더와_업로더_필터를_함께_적용한다() throws Exception {
        Long folderId = folders.create(roomId, "선택 폴더").getId();
        AuthResult other = auth.authenticate("다른 업로더");
        for (long id = 460201; id <= 460204; id++) {
            insert(id, roomId, List.of(1.0, 0.0, 0.0), "test-model");
        }
        jdbc.update("UPDATE stored_file SET uploader_id = ? WHERE id IN (460202, 460204)", other.userId());
        jdbc.update("INSERT INTO folder_media (folder_id, media_id, created_at, updated_at) VALUES (?, 460201, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP), (?, 460202, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)", folderId, folderId);
        for (String filter : List.of("ME", "OTHERS", "ALL")) {
            mvc.perform(get(path()).param("query", "사진").param("folderId", folderId.toString())
                    .param("uploader", filter).header("Authorization", "Bearer " + member.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(filter.equals("ALL") ? 2 : 1))
                .andExpect(jsonPath("$.data.items[0].mediaId").value(filter.equals("OTHERS") ? 460202 : 460201));
        }
    }

    @Test
    void 다른_방_폴더와_없는_폴더는_AI_호출_전에_거절한다() throws Exception {
        Long otherRoom = rooms.create(member.userId(), "다른 방", null, null).room().getId();
        Long otherFolder = folders.create(otherRoom, "다른 폴더").getId();
        for (Long folderId : List.of(otherFolder, Long.MAX_VALUE)) {
            mvc.perform(get(path()).param("query", "사진").param("folderId", folderId.toString())
                    .header("Authorization", "Bearer " + member.accessToken()))
                .andExpect(status().isNotFound());
        }
        org.mockito.Mockito.verifyNoInteractions(embeddings);
    }

    private ResultActions search(String query) throws Exception {
        return mvc.perform(get(path()).param("query", query)
            .header("Authorization", "Bearer " + member.accessToken()));
    }

    private String path() {
        return "/api/v1/rooms/" + roomId + "/media/all";
    }

    private void insert(long id, Long targetRoom, List<Double> vector, String model) {
        jdbc.update("""
            INSERT INTO stored_file (id, room_id, uploader_id, original_file_name, media_type,
                file_size_bytes, storage_key, status, created_at, updated_at, reserved_at)
            VALUES (?, ?, ?, 'photo.jpg', 'JPEG', 100, ?, 'READY',
                CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """, id, targetRoom, member.userId(), "search/" + id + ".jpg");
        documents.register(id, Instant.now());
        AnalysisAttempt attempt = documents.claim(id, Instant.now().plusSeconds(1), 3).orElseThrow();
        documents.complete(attempt, new ImageAnalysis("사진", "바다", "바다 사진", vector,
            model, "vision-test", "v1"), Instant.now());
    }
}
