package com.sssok.presentation.api.media;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sssok.application.auth.AnonymousAuthService;
import com.sssok.application.auth.AuthResult;
import com.sssok.application.folder.CreateFolderService;
import com.sssok.application.port.out.FileStoragePort;
import com.sssok.application.port.out.FileStoragePort.UploadedObject;
import com.sssok.application.room.CreateRoomService;
import com.sssok.domain.folder.Folder;
import com.sssok.domain.room.Room;
import com.sssok.support.PostgresContainerSupport;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

// API 인수 테스트 (Testcontainers). 폴더 담기가 PostgreSQL 전용 ON CONFLICT 를 써서 H2 로는 못 돈다.
// 업로드부터 조회까지 실제 흐름 그대로 태우고, 스토리지만 목으로 둔다.
@SpringBootTest
class MediaQueryApiTest extends PostgresContainerSupport {

    private static final String MIME = "image/jpeg";
    private static final long SIZE = 1024L;

    @Autowired
    WebApplicationContext context;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    AnonymousAuthService anonymousAuthService;

    @Autowired
    CreateRoomService createRoomService;

    @Autowired
    CreateFolderService createFolderService;

    @MockitoBean
    FileStoragePort fileStoragePort;

    private MockMvc mockMvc;
    private Long roomId;
    private String token;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        AuthResult host = anonymousAuthService.authenticate("가현");
        token = "Bearer " + host.accessToken();
        Room room = createRoomService.create(host.userId(), "우테코 회식", null, null).room();
        roomId = room.getId();
        given(fileStoragePort.presignPut(any(), anyString(), any(Duration.class)))
            .willReturn("https://storage.example.com/signed");
        given(fileStoragePort.findUploaded(any()))
            .willReturn(Optional.of(new UploadedObject(SIZE, MIME)));
    }

    @Test
    void 업로드한_미디어가_목록에_나온다() throws Exception {
        Long mediaId = upload("a.jpg", null);

        getMediaList("")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[0].mediaId").value(mediaId))
            .andExpect(jsonPath("$.data.items[0].fileName").value("a.jpg"))
            .andExpect(jsonPath("$.data.items[0].status").value("PROCESSING"))
            .andExpect(jsonPath("$.data.items[0].uploaderName").value("가현"));
    }

    @Test
    void 나중에_올린_미디어가_먼저_나온다() throws Exception {
        Long first = upload("a.jpg", null);
        Long second = upload("b.jpg", null);

        getMediaList("")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[0].mediaId").value(second))
            .andExpect(jsonPath("$.data.items[1].mediaId").value(first));
    }

    // 발급만 받고 스토리지에 올리지 않은 미디어는 실물이 없어 목록에 뜨면 안 된다.
    @Test
    void 발급만_받고_올리지_않은_미디어는_목록에_없다() throws Exception {
        Long uploaded = upload("a.jpg", null);
        issueOnly("b.jpg");

        getMediaList("")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items.length()").value(1))
            .andExpect(jsonPath("$.data.items[0].mediaId").value(uploaded));
    }

    @Test
    void 폴더로_필터하면_그_폴더에_담긴_것만_나온다() throws Exception {
        Folder folder = createFolderService.create(roomId, "1일차");
        Long inFolder = upload("a.jpg", folder.getId());
        upload("b.jpg", null);

        getMediaList("?folderId=" + folder.getId())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items.length()").value(1))
            .andExpect(jsonPath("$.data.items[0].mediaId").value(inFolder))
            .andExpect(jsonPath("$.data.items[0].folderIds[0]").value(folder.getId()));
    }

    @Test
    void 없는_폴더로_필터하면_404() throws Exception {
        getMediaList("?folderId=-1")
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("FOLDER_NOT_FOUND"));
    }

    @Test
    void 단건으로_조회하면_같은_내용이_나온다() throws Exception {
        Folder folder = createFolderService.create(roomId, "1일차");
        Long mediaId = upload("a.jpg", folder.getId());

        mockMvc.perform(get("/api/v1/rooms/{roomId}/media/{mediaId}", roomId, mediaId)
                .header("Authorization", token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.mediaId").value(mediaId))
            .andExpect(jsonPath("$.data.fileName").value("a.jpg"))
            .andExpect(jsonPath("$.data.mimeType").value(MIME))
            .andExpect(jsonPath("$.data.size").value(SIZE))
            .andExpect(jsonPath("$.data.folderIds[0]").value(folder.getId()));
    }

    // 올린 본인이라 지울 수 있고, EXIF 가 없는 이미지라 촬영 정보는 비어 있다.
    @Test
    void 단건에는_촬영_정보와_삭제_권한이_함께_나온다() throws Exception {
        Long mediaId = upload("a.jpg", null);

        mockMvc.perform(get("/api/v1/rooms/{roomId}/media/{mediaId}", roomId, mediaId)
                .header("Authorization", token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.canDelete").value(true))
            .andExpect(jsonPath("$.data.takenAt").doesNotExist())
            .andExpect(jsonPath("$.data.location").doesNotExist());
    }

    @Test
    void 없는_미디어를_조회하면_404() throws Exception {
        mockMvc.perform(get("/api/v1/rooms/{roomId}/media/{mediaId}", roomId, -1L)
                .header("Authorization", token))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("MEDIA_NOT_FOUND"));
    }

    // 입장하지 않은 사용자는 RoomMembershipInterceptor 가 컨트롤러 앞에서 막는다.
    @Test
    void 입장하지_않은_사용자는_403() throws Exception {
        String outsider = "Bearer " + anonymousAuthService.authenticate("의찬").accessToken();

        mockMvc.perform(get("/api/v1/rooms/{roomId}/media", roomId)
                .header("Authorization", outsider))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("NOT_ROOM_MEMBER"));
    }

    private ResultActions getMediaList(String query) throws Exception {
        return mockMvc.perform(get("/api/v1/rooms/" + roomId + "/media" + query)
            .header("Authorization", token));
    }

    // 발급 → 완료 등록까지 한 번에 태워 조회 대상 미디어를 만든다.
    private Long upload(String fileName, Long folderId) throws Exception {
        Long mediaId = issueOnly(fileName, folderId);
        mockMvc.perform(post("/api/v1/rooms/{roomId}/media", roomId)
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"mediaIds\":[%d]}".formatted(mediaId)))
            .andExpect(status().isCreated());
        return mediaId;
    }

    private Long issueOnly(String fileName) throws Exception {
        return issueOnly(fileName, null);
    }

    private Long issueOnly(String fileName, Long folderId) throws Exception {
        String folders = folderId == null ? "" : ",\"folderIds\":[%d]".formatted(folderId);
        String body = "{\"files\":[{\"fileName\":\"%s\",\"mimeType\":\"%s\",\"size\":%d}]%s}"
            .formatted(fileName, MIME, SIZE, folders);

        String response = mockMvc.perform(post("/api/v1/rooms/{roomId}/media/upload-urls", roomId)
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        JsonNode root = objectMapper.readTree(response);
        return root.path("data").path("issued").get(0).path("mediaId").asLong();
    }
}
