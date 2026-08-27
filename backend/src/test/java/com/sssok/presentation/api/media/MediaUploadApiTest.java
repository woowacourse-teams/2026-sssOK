package com.sssok.presentation.api.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
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
import com.sssok.application.room.JoinRoomService;
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
// 스토리지만 목으로 두고 나머지는 실제 흐름 그대로 태운다.
@SpringBootTest
class MediaUploadApiTest extends PostgresContainerSupport {

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

    @Autowired
    JoinRoomService joinRoomService;

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
    }

    private ResultActions issue(String body) throws Exception {
        return mockMvc.perform(post("/api/v1/rooms/{roomId}/media/upload-urls", roomId)
            .header("Authorization", token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
    }

    private String oneImage() {
        return "{\"files\":[{\"fileName\":\"a.jpg\",\"mimeType\":\"%s\",\"size\":%d}]}"
            .formatted(MIME, SIZE);
    }

    private Long issuedMediaId(String responseBody) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);
        return root.path("data").path("issued").get(0).path("mediaId").asLong();
    }

    @Test
    void 발급부터_완료_등록까지_이어진다() throws Exception {
        String issued = issue(oneImage()).andExpect(status().isOk()).andReturn()
            .getResponse().getContentAsString();
        Long mediaId = issuedMediaId(issued);
        given(fileStoragePort.findUploaded(any()))
            .willReturn(Optional.of(new UploadedObject(SIZE, MIME)));

        mockMvc.perform(post("/api/v1/rooms/{roomId}/media", roomId)
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"mediaIds\":[%d]}".formatted(mediaId)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.registered[0].mediaId").value(mediaId))
            .andExpect(jsonPath("$.data.registered[0].status").value("PROCESSING"))
            .andExpect(jsonPath("$.data.registered[0].type").value("IMAGE"))
            .andExpect(jsonPath("$.data.registered[0].uploaderName").value("가현"));
    }

    @Test
    void 폴더를_지정하면_발급과_동시에_담긴다() throws Exception {
        Folder folder = createFolderService.create(roomId, "1일차");
        String body = "{\"files\":[{\"fileName\":\"a.jpg\",\"mimeType\":\"%s\",\"size\":%d}],\"folderIds\":[%d]}"
            .formatted(MIME, SIZE, folder.getId());

        String issued = issue(body).andExpect(status().isOk()).andReturn()
            .getResponse().getContentAsString();
        Long mediaId = issuedMediaId(issued);
        given(fileStoragePort.findUploaded(any()))
            .willReturn(Optional.of(new UploadedObject(SIZE, MIME)));

        mockMvc.perform(post("/api/v1/rooms/{roomId}/media", roomId)
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"mediaIds\":[%d]}".formatted(mediaId)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.registered[0].folderIds[0]").value(folder.getId()));
    }

    @Test
    void 없는_폴더를_지정하면_404() throws Exception {
        String body = "{\"files\":[{\"fileName\":\"a.jpg\",\"mimeType\":\"%s\",\"size\":%d}],\"folderIds\":[-1]}"
            .formatted(MIME, SIZE);

        issue(body)
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("FOLDER_NOT_FOUND"));
    }

    @Test
    void 올리지_않고_등록하면_실패로_보고된다() throws Exception {
        String issued = issue(oneImage()).andReturn().getResponse().getContentAsString();
        Long mediaId = issuedMediaId(issued);
        given(fileStoragePort.findUploaded(any())).willReturn(Optional.empty());

        mockMvc.perform(post("/api/v1/rooms/{roomId}/media", roomId)
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"mediaIds\":[%d]}".formatted(mediaId)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.registered").isEmpty())
            .andExpect(jsonPath("$.data.failed[0].code").value("UPLOAD_NOT_COMPLETED"));
    }

    @Test
    void 재발급하면_같은_미디어_ID로_새_URL을_받는다() throws Exception {
        String issued = issue(oneImage()).andReturn().getResponse().getContentAsString();
        Long mediaId = issuedMediaId(issued);

        mockMvc.perform(post("/api/v1/rooms/{roomId}/media/{mediaId}/upload-url", roomId, mediaId)
                .header("Authorization", token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.mediaId").value(mediaId))
            .andExpect(jsonPath("$.data.retryCount").value(1));
    }

    @Test
    void 남의_예약은_재발급할_수_없다() throws Exception {
        String issued = issue(oneImage()).andReturn().getResponse().getContentAsString();
        Long mediaId = issuedMediaId(issued);

        AuthResult guest = anonymousAuthService.authenticate("민수");
        joinRoomService.join(roomId, guest.userId());

        mockMvc.perform(post("/api/v1/rooms/{roomId}/media/{mediaId}/upload-url", roomId, mediaId)
                .header("Authorization", "Bearer " + guest.accessToken()))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("MEDIA_FORBIDDEN"));
    }

    @Test
    void 입장하지_않은_사용자는_발급받을_수_없다() throws Exception {
        AuthResult outsider = anonymousAuthService.authenticate("외부인");

        mockMvc.perform(post("/api/v1/rooms/{roomId}/media/upload-urls", roomId)
                .header("Authorization", "Bearer " + outsider.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(oneImage()))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("NOT_ROOM_MEMBER"));
    }

    @Test
    void 걸러진_파일은_rejected_로_내려온다() throws Exception {
        String body = "{\"files\":["
            + "{\"fileName\":\"a.jpg\",\"mimeType\":\"image/jpeg\",\"size\":1024},"
            + "{\"fileName\":\"note.pdf\",\"mimeType\":\"application/pdf\",\"size\":1024}]}";

        String response = issue(body).andExpect(status().isOk()).andReturn()
            .getResponse().getContentAsString();

        JsonNode data = objectMapper.readTree(response).path("data");
        assertThat(data.path("issued")).hasSize(1);
        assertThat(data.path("rejected").get(0).path("code").asText())
            .isEqualTo("UNSUPPORTED_MEDIA_TYPE");
    }
}
