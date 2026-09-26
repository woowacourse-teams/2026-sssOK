package com.sssok.presentation.api.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;
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
    private String roomCode;
    private String token;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        AuthResult host = anonymousAuthService.authenticate("가현");
        token = "Bearer " + host.accessToken();
        Room room = createRoomService.create(host.userId(), "우테코 회식", null, null).room();
        roomId = room.getId();
        roomCode = room.getCode().value();
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

    @Test
    void 커서로_다음_페이지를_조회한다() throws Exception {
        Long first = upload("a.jpg", null);
        Long second = upload("b.jpg", null);
        Long third = upload("c.jpg", null);

        String firstResponse = getMediaList("?size=2")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[0].mediaId").value(third))
            .andExpect(jsonPath("$.data.items[1].mediaId").value(second))
            .andExpect(jsonPath("$.data.hasNext").value(true))
            .andExpect(jsonPath("$.data.nextCursor").isString())
            .andExpect(jsonPath("$.data.totalCount").value(3))
            .andReturn().getResponse().getContentAsString();
        String cursor = objectMapper.readTree(firstResponse).path("data").path("nextCursor").asText();

        getMediaList("?size=2&cursor=" + cursor)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items.length()").value(1))
            .andExpect(jsonPath("$.data.items[0].mediaId").value(first))
            .andExpect(jsonPath("$.data.hasNext").value(false))
            .andExpect(jsonPath("$.data.nextCursor").doesNotExist())
            .andExpect(jsonPath("$.data.totalCount").value(3));
    }

    @Test
    void 전체_목록은_모든_미디어를_페이지네이션_필드_없이_반환한다() throws Exception {
        Long first = upload("a.jpg", null);
        Long second = upload("b.jpg", null);
        Long third = upload("c.jpg", null);

        getAllMedia("")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items.length()").value(3))
            .andExpect(jsonPath("$.data.items[0].mediaId").value(third))
            .andExpect(jsonPath("$.data.items[1].mediaId").value(second))
            .andExpect(jsonPath("$.data.items[2].mediaId").value(first))
            .andExpect(jsonPath("$.data.nextCursor").doesNotExist())
            .andExpect(jsonPath("$.data.hasNext").doesNotExist())
            .andExpect(jsonPath("$.data.totalCount").doesNotExist());
    }

    @Test
    void 전체_목록도_폴더로_필터할_수_있다() throws Exception {
        Folder folder = createFolderService.create(roomId, "1일차");
        Long inFolder = upload("a.jpg", folder.getId());
        upload("b.jpg", null);

        getAllMedia("?folderId=" + folder.getId())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items.length()").value(1))
            .andExpect(jsonPath("$.data.items[0].mediaId").value(inFolder));
    }

    @Test
    void 올바르지_않은_커서는_400() throws Exception {
        getMediaList("?cursor=invalid-cursor")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_CURSOR"));
    }

    @Test
    void JSON_null인_커서는_400() throws Exception {
        getMediaList("?cursor=bnVsbA")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_CURSOR"));
    }

    @Test
    void 페이지_크기가_허용_범위를_벗어나면_400() throws Exception {
        getMediaList("?size=101")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_PAGE_SIZE"));
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

    // photoCount 는 매핑 행이 아니라 목록에 실제로 보이는 미디어를 세야 한다.
    // 삭제된 미디어(매핑도 함께 끊긴다)와 발급만 받은 미디어(RESERVED)가 섞여 있어도 목록 개수와 같아야 한다.
    @Test
    void 폴더_photoCount_는_목록_개수와_같다() throws Exception {
        Folder folder = createFolderService.create(roomId, "1일차");
        Long visible = upload("a.jpg", folder.getId());
        Long deleted = upload("b.jpg", folder.getId());
        issueOnly("c.jpg", folder.getId());
        mockMvc.perform(delete("/api/v1/rooms/{roomId}/media/{mediaId}", roomId, deleted)
                .header("Authorization", token))
            .andExpect(status().isOk());

        getMediaList("?folderId=" + folder.getId())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items.length()").value(1))
            .andExpect(jsonPath("$.data.items[0].mediaId").value(visible))
            .andExpect(jsonPath("$.data.totalCount").value(1));
        mockMvc.perform(get("/api/v1/rooms/{code}", roomCode)
                .header("Authorization", token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.photoCount").value(1))
            .andExpect(jsonPath("$.data.folders[0].id").value(folder.getId()))
            .andExpect(jsonPath("$.data.folders[0].photoCount").value(1));
    }

    // 담기·꺼내기 응답의 photoCount 도 방 상세와 같은 기준을 써야 화면이 서로 어긋나지 않는다.
    @Test
    void 담기와_꺼내기_응답의_photoCount_도_보이는_것만_센다() throws Exception {
        Folder folder = createFolderService.create(roomId, "1일차");
        Long visible = upload("a.jpg", null);
        Long reserved = issueOnly("b.jpg");

        addToFolder(folder.getId(), visible, reserved)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.folder.photoCount").value(1));

        removeFromFolder(folder.getId(), visible)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.folders[0].photoCount").value(0));
    }

    // 전체 선택(exclude 모드) 담기는 방의 모든 stored_file 을 대상으로 잡는다.
    // 아직 올라오지 않은 RESERVED 미디어까지 담기면, 담았다는 개수와 폴더 개수가 어긋난다.
    @Test
    void 전체_선택_담기의_결과_개수와_폴더_photoCount_가_같다() throws Exception {
        Folder folder = createFolderService.create(roomId, "1일차");
        upload("a.jpg", null);
        issueOnly("b.jpg");

        String response = addToFolderAll(folder.getId())
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        JsonNode data = objectMapper.readTree(response).path("data");

        int moved = data.path("updatedCount").asInt() + data.path("alreadyInCount").asInt();
        assertThat(moved).isEqualTo(data.path("folder").path("photoCount").asInt());
    }

    // 폴더를 지정해 업로드하면 발급 시점에 이미 folder_media 매핑이 생긴다.
    // 아직 실물이 없는 동안에는 목록에도 photoCount 에도 잡히지 않아야 하고, 완료 등록되면 둘 다 함께 올라야 한다.
    @Test
    void 폴더에_업로드_중인_사진은_완료된_뒤에야_개수에_잡힌다() throws Exception {
        Folder folder = createFolderService.create(roomId, "1일차");
        Long mediaId = issueOnly("a.jpg", folder.getId());

        assertThat(folderPhotoCount(folder.getId())).isZero();
        getMediaList("?folderId=" + folder.getId())
            .andExpect(jsonPath("$.data.totalCount").value(0));

        finishUpload(mediaId);

        assertThat(folderPhotoCount(folder.getId())).isEqualTo(1);
        getMediaList("?folderId=" + folder.getId())
            .andExpect(jsonPath("$.data.totalCount").value(1));
    }

    // 전체 선택 꺼내기도 담기와 같은 기준을 써야, 꺼낸 개수와 남은 개수의 합이 원래 개수와 맞는다.
    @Test
    void 전체_선택_꺼내기의_결과_개수와_폴더_photoCount_가_같다() throws Exception {
        Folder folder = createFolderService.create(roomId, "1일차");
        upload("a.jpg", folder.getId());
        upload("b.jpg", folder.getId());
        issueOnly("c.jpg", folder.getId());
        int before = folderPhotoCount(folder.getId());

        String response = removeFromFolderAll(folder.getId())
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        JsonNode data = objectMapper.readTree(response).path("data");

        assertThat(before).isEqualTo(2);
        assertThat(data.path("updatedCount").asInt()).isEqualTo(before);
        assertThat(data.path("folders").get(0).path("photoCount").asInt()).isZero();
        assertThat(folderPhotoCount(folder.getId())).isZero();
    }

    private int folderPhotoCount(Long folderId) throws Exception {
        String response = mockMvc.perform(get("/api/v1/rooms/{code}", roomCode)
                .header("Authorization", token))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        for (JsonNode folder : objectMapper.readTree(response).path("data").path("folders")) {
            if (folder.path("id").asLong() == folderId) {
                return folder.path("photoCount").asInt();
            }
        }
        throw new IllegalStateException("폴더를 찾지 못했다: " + folderId);
    }

    private void finishUpload(Long mediaId) throws Exception {
        mockMvc.perform(post("/api/v1/rooms/{roomId}/media", roomId)
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"mediaIds\":[%d]}".formatted(mediaId)))
            .andExpect(status().isCreated());
    }

    private ResultActions removeFromFolderAll(Long folderId) throws Exception {
        return mockMvc.perform(delete("/api/v1/rooms/{roomId}/media/folders", roomId)
            .header("Authorization", token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"selection\":{\"mode\":\"exclude\",\"ids\":[]},\"folderIds\":[%d]}"
                .formatted(folderId)));
    }

    private ResultActions addToFolderAll(Long folderId) throws Exception {
        return mockMvc.perform(put("/api/v1/rooms/{roomId}/media/folders", roomId)
            .header("Authorization", token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"selection\":{\"mode\":\"exclude\",\"ids\":[]},\"folderId\":%d}"
                .formatted(folderId)));
    }

    private ResultActions addToFolder(Long folderId, Long... mediaIds) throws Exception {
        return mockMvc.perform(put("/api/v1/rooms/{roomId}/media/folders", roomId)
            .header("Authorization", token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"selection\":{\"mode\":\"include\",\"ids\":[%s]},\"folderId\":%d}"
                .formatted(joinIds(mediaIds), folderId)));
    }

    private ResultActions removeFromFolder(Long folderId, Long... mediaIds) throws Exception {
        return mockMvc.perform(delete("/api/v1/rooms/{roomId}/media/folders", roomId)
            .header("Authorization", token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"selection\":{\"mode\":\"include\",\"ids\":[%s]},\"folderIds\":[%d]}"
                .formatted(joinIds(mediaIds), folderId)));
    }

    private String joinIds(Long... mediaIds) {
        return Arrays.stream(mediaIds).map(String::valueOf).collect(Collectors.joining(","));
    }

    private ResultActions getMediaList(String query) throws Exception {
        return mockMvc.perform(get("/api/v1/rooms/" + roomId + "/media" + query)
            .header("Authorization", token));
    }

    private ResultActions getAllMedia(String query) throws Exception {
        return mockMvc.perform(get("/api/v1/rooms/" + roomId + "/media/all" + query)
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
