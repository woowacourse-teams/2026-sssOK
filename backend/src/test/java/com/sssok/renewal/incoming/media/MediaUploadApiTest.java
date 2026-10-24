package com.sssok.renewal.incoming.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sssok.renewal.application.auth.AnonymousAuthService;
import com.sssok.renewal.application.auth.AuthResult;
import com.sssok.application.folder.CreateFolderService;
import com.sssok.application.port.out.FileRepository;
import com.sssok.application.port.out.FolderMediaRepository;
import com.sssok.renewal.outgoing.storage.FileStorage;
import com.sssok.renewal.outgoing.storage.FileStorage.UploadedObject;
import com.sssok.application.room.CreateRoomService;
import com.sssok.application.room.JoinRoomService;
import com.sssok.domain.file.UploadStatus;
import com.sssok.domain.file.StorageKey;
import com.sssok.domain.folder.Folder;
import com.sssok.domain.room.Room;
import com.sssok.support.PostgresContainerSupport;
import com.sssok.support.AcceptanceTest;
import java.time.Duration;
import java.util.Optional;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.sssok.application.media.MediaCreatedEvent;
import com.sssok.application.media.MediaReadyEvent;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

// API 인수 테스트 (Testcontainers). 폴더 담기가 PostgreSQL 전용 ON CONFLICT 를 써서 H2 로는 못 돈다.
// 스토리지만 목으로 두고 나머지는 실제 흐름 그대로 태운다.
@AcceptanceTest
@RecordApplicationEvents
@SpringBootTest(properties = {
    "jwt.secret=bG9jYWwtY29udHJhY3QtdGVzdC1rZXktbm90LWZvci1wcm9kdWN0aW9uLTU1Nw==",
    "upload.image-max-size=20MB", "upload.presigned-url-ttl=600s"
})
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
    FileStorage fileStorage;

    @Autowired
    FileRepository fileRepository;

    @Autowired
    FolderMediaRepository folderMediaRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    ApplicationEvents events;

    @Autowired
    RequestMappingHandlerMapping requestMappingHandlerMapping;

    private MockMvc mockMvc;
    private Long roomId;
    private String token;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.when(fileStorage.presignGet(
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any()))
            .thenReturn("https://fileStorage.example.com/read");
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        AuthResult host = anonymousAuthService.authenticate("가현");
        token = "Bearer " + host.accessToken();
        Room room = createRoomService.create(host.userId(), "우테코 회식", null).room();
        roomId = room.getId();
        given(fileStorage.presignPut(any(), anyString(), any(Duration.class)))
            .willReturn("https://fileStorage.example.com/signed");
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
        given(fileStorage.findUploaded(any()))
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
        given(fileStorage.findUploaded(any()))
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
        given(fileStorage.findUploaded(any())).willReturn(Optional.empty());

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
            .andExpect(jsonPath("$.data.uploadUrl").value("https://fileStorage.example.com/signed"))
            .andExpect(jsonPath("$.data.method").value("PUT"))
            .andExpect(jsonPath("$.data.headers['Content-Type']").value("image/jpeg"))
            .andExpect(jsonPath("$.data.expiresIn").value(600))
            .andExpect(jsonPath("$.data.maxRetryCount").value(5))
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

    // 예전에는 파일명 없이도 예약을 시도해서 stored_file NOT NULL 제약에 걸려 500 이 나갔다.
    @Test
    void 파일명이_없는_파일은_rejected_로_내려오고_500이_되지_않는다() throws Exception {
        String body = "{\"files\":[{\"fileName\":null,\"mimeType\":\"" + MIME + "\",\"size\":" + SIZE + "}]}";

        issue(body)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.issued").isEmpty())
            .andExpect(jsonPath("$.data.rejected[0].code").value("INVALID_PARAM"));
    }

    @Test
    void files에_null이_섞여_있으면_400() throws Exception {
        issue("{\"files\":[null]}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_PARAM"));
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

    @Test
    void 일부_거절과_동일_파일명에도_예약과_서명_URL이_정확히_대응한다() throws Exception {
        given(fileStorage.presignPut(any(), anyString(), any(Duration.class)))
            .willAnswer(invocation -> "https://fileStorage.example.com/"
                + ((StorageKey) invocation.getArgument(0)).value());
        String body = """
            {"files":[
              {"fileName":"same.jpg","mimeType":" IMAGE/JPEG ","size":1024},
              {"fileName":"note.pdf","mimeType":"application/pdf","size":1024},
              {"fileName":"same.jpg","mimeType":"image/png","size":2048}
            ]}
            """;

        JsonNode data = objectMapper.readTree(issue(body).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString()).path("data");

        assertThat(data.path("issued")).hasSize(2);
        assertThat(data.path("rejected")).hasSize(1);
        assertThat(data.path("rejected").get(0).path("fileName").asText()).isEqualTo("note.pdf");
        assertThat(fileRepository.findAllByRoomId(roomId)).hasSize(2);
        List<String> types = List.of("image/jpeg", "image/png");
        List<Long> sizes = List.of(1024L, 2048L);
        for (int i = 0; i < 2; i++) {
            JsonNode issued = data.path("issued").get(i);
            com.sssok.domain.file.StoredFile stored = fileRepository.findById(issued.path("mediaId").asLong()).orElseThrow();
            assertThat(stored.getStatus()).isEqualTo(UploadStatus.RESERVED);
            assertThat(stored.getFileSize().bytes()).isEqualTo(sizes.get(i));
            assertThat(issued.path("fileName").asText()).isEqualTo("same.jpg");
            assertThat(issued.path("uploadUrl").asText())
                .isEqualTo("https://fileStorage.example.com/" + stored.getStorageKey().value());
            assertThat(issued.path("headers").path("Content-Type").asText()).isEqualTo(types.get(i));
            org.mockito.Mockito.verify(fileStorage).presignPut(
                stored.getStorageKey(), types.get(i), Duration.ofSeconds(issued.path("expiresIn").asInt()));
        }
        assertThat(data.path("issued").get(0).path("mediaId").asLong())
            .isNotEqualTo(data.path("issued").get(1).path("mediaId").asLong());
    }

    @Test
    void 모든_파일이_거절되면_200이며_예약과_폴더_연결이_없다() throws Exception {
        Folder folder = createFolderService.create(roomId, "거절 확인");
        String body = """
            {"files":[
              {"fileName":"note.pdf","mimeType":"application/pdf","size":1024},
              {"fileName":"empty.jpg","mimeType":"image/jpeg","size":0}
            ],"folderIds":[%d]}
            """.formatted(folder.getId());

        issue(body).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.issued").isEmpty())
            .andExpect(jsonPath("$.data.rejected.length()").value(2))
            .andExpect(jsonPath("$.data.rejected[0].code").value("UNSUPPORTED_MEDIA_TYPE"))
            .andExpect(jsonPath("$.data.rejected[1].code").value("INVALID_PARAM"));

        assertThat(fileRepository.findAllByRoomId(roomId)).isEmpty();
        assertThat(folderMediaRepository.findMediaIdsByFolderId(folder.getId())).isEmpty();
        org.mockito.Mockito.verify(fileStorage, org.mockito.Mockito.never())
            .presignPut(any(), anyString(), any(Duration.class));
    }

    @Test
    void 다른_방_폴더가_섞이면_전체_실패하며_예약하지_않는다() throws Exception {
        Folder own = createFolderService.create(roomId, "현재 방");
        AuthResult other = anonymousAuthService.authenticate("다른 방장");
        Long otherRoomId = createRoomService.create(other.userId(), "다른 방", null).room().getId();
        Folder foreign = createFolderService.create(otherRoomId, "다른 방 폴더");
        String body = """
            {"files":[{"fileName":"a.jpg","mimeType":"image/jpeg","size":1024}],
             "folderIds":[%d,%d]}
            """.formatted(own.getId(), foreign.getId());

        issue(body).andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("FOLDER_NOT_FOUND"));

        assertThat(fileRepository.findAllByRoomId(roomId)).isEmpty();
        assertThat(folderMediaRepository.findMediaIdsByFolderId(own.getId())).isEmpty();
        assertThat(folderMediaRepository.findMediaIdsByFolderId(foreign.getId())).isEmpty();
    }

    @Test
    void 두번째_서명이_실패하면_예약과_폴더_연결이_모두_롤백된다() throws Exception {
        Folder folder = createFolderService.create(roomId, "롤백 확인");
        AtomicInteger signatures = new AtomicInteger();
        given(fileStorage.presignPut(any(), anyString(), any(Duration.class)))
            .willAnswer(invocation -> {
                if (signatures.incrementAndGet() == 2) {
                    throw new IllegalStateException("서명 실패");
                }
                return "https://fileStorage.example.com/first";
            });
        String body = """
            {"files":[
              {"fileName":"a.jpg","mimeType":"image/jpeg","size":1024},
              {"fileName":"b.jpg","mimeType":"image/jpeg","size":1024}
            ],"folderIds":[%d]}
            """.formatted(folder.getId());

        issue(body).andExpect(status().isInternalServerError());

        assertThat(signatures.get()).isEqualTo(2);
        assertThat(fileRepository.findAllByRoomId(roomId)).isEmpty();
        // 조인 조회는 고아 연결을 숨길 수 있으므로 연결 테이블을 직접 확인한다.
        assertThat(jdbcTemplate.queryForObject(
            "SELECT count(*) FROM folder_media WHERE folder_id = ?", Long.class, folder.getId()))
            .isZero();
    }

    @Test
    void 중복_폴더를_지정해도_예약당_연결은_하나만_생긴다() throws Exception {
        Folder folder = createFolderService.create(roomId, "중복 폴더");
        String body = """
            {"files":[{"fileName":"a.jpg","mimeType":"image/jpeg","size":1024}],
             "folderIds":[%d,%d]}
            """.formatted(folder.getId(), folder.getId());

        Long mediaId = issuedMediaId(issue(body).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString());

        assertThat(folderMediaRepository.findMediaIdsByFolderId(folder.getId())).containsExactly(mediaId);
        assertThat(fileRepository.findById(mediaId).orElseThrow().getStatus()).isEqualTo(UploadStatus.RESERVED);
    }

    @Test
    void 발급_경로는_하나의_컨트롤러만_처리한다() {
        List<Class<?>> handlers = requestMappingHandlerMapping.getHandlerMethods().entrySet().stream()
            .filter(entry -> entry.getKey().getPatternValues()
                .contains("/api/v1/rooms/{roomId}/media/upload-urls"))
            .<Class<?>>map(entry -> entry.getValue().getBeanType())
            .toList();
        assertThat(handlers).hasSize(1);
        assertThat(handlers.get(0)).isEqualTo(MediaUploadController.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"files\":null}", "{\"files\":[]}", "{\"files\":[null]}"})
    void 잘못된_파일_목록은_전체_400이며_예약하지_않는다(String body) throws Exception {
        issue(body).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_PARAM"));
        assertThat(fileRepository.findAllByRoomId(roomId)).isEmpty();
    }

    @Test
    void 본문이_없으면_400이며_예약하지_않는다() throws Exception {
        mockMvc.perform(post("/api/v1/rooms/{roomId}/media/upload-urls", roomId)
                .header("Authorization", token).contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST_BODY"));
        assertThat(fileRepository.findAllByRoomId(roomId)).isEmpty();
    }

    @Test
    void 인증이_없으면_401이며_예약하지_않는다() throws Exception {
        mockMvc.perform(post("/api/v1/rooms/{roomId}/media/upload-urls", roomId)
                .contentType(MediaType.APPLICATION_JSON).content(oneImage()))
            .andExpect(status().isUnauthorized());
        assertThat(fileRepository.findAllByRoomId(roomId)).isEmpty();
    }

    @Test
    void 발급만으로_미디어_생성이나_READY_이벤트를_발행하지_않는다() throws Exception {
        issue(oneImage()).andExpect(status().isOk());
        assertThat(events.stream(MediaCreatedEvent.class)).isEmpty();
        assertThat(events.stream(MediaReadyEvent.class)).isEmpty();
        assertThat(fileRepository.findAllByRoomId(roomId))
            .hasSize(1).allSatisfy(file -> assertThat(file.getStatus()).isEqualTo(UploadStatus.RESERVED));
    }

    @Test
    void 방장만_업로드하는_방의_참여자는_발급할_수_없다() throws Exception {
        AuthResult host = anonymousAuthService.authenticate("방장");
        Long restrictedRoom = createRoomService.create(host.userId(), "방장만 업로드", "host").room().getId();
        AuthResult guest = anonymousAuthService.authenticate("참여자");
        joinRoomService.join(restrictedRoom, guest.userId());

        mockMvc.perform(post("/api/v1/rooms/{roomId}/media/upload-urls", restrictedRoom)
                .header("Authorization", "Bearer " + guest.accessToken())
                .contentType(MediaType.APPLICATION_JSON).content(oneImage()))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("UPLOAD_NOT_ALLOWED"));
        assertThat(fileRepository.findAllByRoomId(restrictedRoom)).isEmpty();
    }

    @Test
    void 이미지_상한은_허용하고_상한_초과와_크기_누락은_파일별로_거절한다() throws Exception {
        String body = """
            {"files":[
              {"fileName":"limit.jpg","mimeType":"image/jpeg","size":20971520},
              {"fileName":"large.jpg","mimeType":"image/jpeg","size":20971521},
              {"fileName":"missing.jpg","mimeType":"image/jpeg"}
            ]}
            """;
        issue(body).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.issued.length()").value(1))
            .andExpect(jsonPath("$.data.issued[0].fileName").value("limit.jpg"))
            .andExpect(jsonPath("$.data.issued[0].method").value("PUT"))
            .andExpect(jsonPath("$.data.issued[0].expiresIn").value(600))
            .andExpect(jsonPath("$.data.rejected.length()").value(2))
            .andExpect(jsonPath("$.data.rejected[0].code").value("FILE_TOO_LARGE"))
            .andExpect(jsonPath("$.data.rejected[1].code").value("INVALID_PARAM"));
        assertThat(fileRepository.findAllByRoomId(roomId)).hasSize(1);
    }

    @Test
    void develop의_응답_필드와_실패_메시지를_유지한다() throws Exception {
        String body = """
            {"files":[
              {"fileName":"a.jpg","mimeType":"image/jpeg","size":1024},
              {"fileName":"a.pdf","mimeType":"application/pdf","size":1024}
            ]}
            """;
        JsonNode data = objectMapper.readTree(issue(body).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString()).path("data");
        assertThat(data.properties()).extracting(java.util.Map.Entry::getKey)
            .containsExactlyInAnyOrder("issued", "rejected");
        assertThat(data.path("issued").get(0).properties()).extracting(java.util.Map.Entry::getKey)
            .containsExactlyInAnyOrder("mediaId", "fileName", "uploadUrl", "method", "headers", "expiresIn");
        assertThat(data.path("rejected").get(0).properties()).extracting(java.util.Map.Entry::getKey)
            .containsExactlyInAnyOrder("fileName", "code", "message");
        assertThat(data.path("rejected").get(0).path("message").asText())
            .isEqualTo("이미지와 영상만 업로드할 수 있습니다");
    }

    @Test
    void 잘못된_폴더는_파일별_검증보다_먼저_요청_전체를_실패시킨다() throws Exception {
        issue("""
            {"files":[{"fileName":null,"mimeType":"application/pdf","size":0}],"folderIds":[-1]}
            """)
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("FOLDER_NOT_FOUND"))
            .andExpect(jsonPath("$.message").value("존재하지 않는 폴더입니다: [-1]"));
        assertThat(fileRepository.findAllByRoomId(roomId)).isEmpty();
        org.mockito.Mockito.verify(fileStorage, org.mockito.Mockito.never())
            .presignPut(any(), anyString(), any(Duration.class));
    }

    @Test
    void 최상위_폴더_목록의_null은_전체_400이다() throws Exception {
        issue("""
            {"files":[{"fileName":"a.jpg","mimeType":"image/jpeg","size":1024}],"folderIds":[null]}
            """)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_PARAM"));
        assertThat(fileRepository.findAllByRoomId(roomId)).isEmpty();
    }
}
