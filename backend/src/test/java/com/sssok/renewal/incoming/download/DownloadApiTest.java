package com.sssok.renewal.incoming.download;

import static com.sssok.support.UploadSizePolicyFixture.SIZE_POLICY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sssok.application.auth.AnonymousAuthService;
import com.sssok.application.auth.AuthResult;
import com.sssok.application.folder.CreateFolderService;
import com.sssok.application.media.DeleteMediaService;
import com.sssok.application.port.out.FileRepository;
import com.sssok.application.port.out.FolderMediaRepository;
import com.sssok.application.room.CreateRoomService;
import com.sssok.application.room.JoinRoomService;
import com.sssok.domain.file.FileSize;
import com.sssok.domain.file.StorageKey;
import com.sssok.domain.file.StoredFile;
import com.sssok.domain.file.UploadStatus;
import com.sssok.renewal.outgoing.storage.FileStorage;
import com.sssok.support.AcceptanceTest;
import com.sssok.support.PostgresContainerSupport;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

// #581 단건·다건 다운로드 API 계약. 구조를 바꾸기 전 코드와 바꾼 뒤 코드가 이 테스트를 똑같이 통과해야 한다.
// 스토리지만 목으로 두고, 서명 URL에 저장소 키와 Content-Disposition 을 그대로 실어 돌려줘서
// 응답의 파일·파일명·서명 대상이 서로 맞물리는지 확인한다.
@AcceptanceTest
@SpringBootTest(properties = {
    "jwt.secret=bG9jYWwtY29udHJhY3QtdGVzdC1rZXktbm90LWZvci1wcm9kdWN0aW9uLTU4MQ==",
    "download.presigned-get-ttl=5m"
})
class DownloadApiTest extends PostgresContainerSupport {

    private static final Duration PRESIGNED_GET_TTL = Duration.ofMinutes(5);

    @Autowired
    WebApplicationContext context;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    AnonymousAuthService anonymousAuthService;

    @Autowired
    CreateRoomService createRoomService;

    @Autowired
    JoinRoomService joinRoomService;

    @Autowired
    CreateFolderService createFolderService;

    @Autowired
    FolderMediaRepository folderMediaRepository;

    @Autowired
    FileRepository fileRepository;

    @Autowired
    DeleteMediaService deleteMediaService;

    @Autowired
    TransactionTemplate transactionTemplate;

    @MockitoBean
    FileStorage fileStorage;

    private MockMvc mockMvc;
    private Long hostId;
    private String hostToken;
    private Long roomId;
    private Long guestId;
    private String guestToken;
    private Long otherRoomId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        given(fileStorage.presignGet(any(), anyString(), anyString(), any(Duration.class)))
            .willAnswer(invocation -> signedUrl(invocation.getArgument(0), invocation.getArgument(1)));

        AuthResult host = anonymousAuthService.authenticate("가현");
        hostId = host.userId();
        hostToken = "Bearer " + host.accessToken();
        roomId = createRoomService.create(hostId, "제주 여행", null).room().getId();

        AuthResult guest = anonymousAuthService.authenticate("민수");
        guestId = guest.userId();
        guestToken = "Bearer " + guest.accessToken();
        joinRoomService.join(roomId, guestId);

        AuthResult stranger = anonymousAuthService.authenticate("지수");
        otherRoomId = createRoomService.create(stranger.userId(), "다른 방", null).room().getId();
    }

    private static String signedUrl(StorageKey storageKey, String contentDisposition) {
        return "https://storage.test/" + storageKey.value()
            + "?disposition=" + URLEncoder.encode(contentDisposition, StandardCharsets.UTF_8);
    }

    private Long media(Long roomId, Long uploaderId, String fileName, UploadStatus status) {
        StoredFile file = StoredFile.reserve(
            roomId, uploaderId, fileName, "image/jpeg", new FileSize(1024), Instant.now(), SIZE_POLICY);
        switch (status) {
            case PROCESSING -> file.startProcessing();
            case READY -> {
                file.startProcessing();
                file.markReady();
            }
            case FAILED -> file.failUpload();
            default -> {
            }
        }
        return fileRepository.save(file).getId();
    }

    private Long readyMedia(String fileName) {
        return media(roomId, hostId, fileName, UploadStatus.READY);
    }

    private Long folderWith(String name, Long... mediaIds) {
        Long folderId = createFolderService.create(roomId, name).getId();
        transactionTemplate.executeWithoutResult(
            status -> folderMediaRepository.attachToFolder(folderId, List.of(mediaIds)));
        return folderId;
    }

    private StorageKey storageKeyOf(Long mediaId) {
        return fileRepository.findById(mediaId).orElseThrow().getStorageKey();
    }

    @Nested
    class 단건_다운로드 {

        private ResultActions download(Long mediaId, String token) throws Exception {
            return mockMvc.perform(get("/api/v1/rooms/{roomId}/downloads/media/{mediaId}", roomId, mediaId)
                .header("Authorization", token));
        }

        @Test
        void 원본_서명_URL로_302_리다이렉트하고_본문은_없다() throws Exception {
            Long mediaId = readyMedia("IMG 01.jpg");
            String contentDisposition = "attachment; filename=\"IMG 01.jpg\"; filename*=UTF-8''IMG%2001.jpg";

            download(mediaId, hostToken)
                .andExpect(status().isFound())
                .andExpect(header().string("Location", signedUrl(storageKeyOf(mediaId), contentDisposition)))
                .andExpect(content().string(""));

            verify(fileStorage).presignGet(
                storageKeyOf(mediaId), contentDisposition, "image/jpeg", PRESIGNED_GET_TTL);
        }

        @Test
        void 한글_파일명은_ASCII_폴백과_UTF8_인코딩을_함께_싣는다() throws Exception {
            Long mediaId = readyMedia("제주 바다.jpg");

            download(mediaId, hostToken).andExpect(status().isFound());

            verify(fileStorage).presignGet(storageKeyOf(mediaId),
                "attachment; filename=\"download.jpg\"; filename*=UTF-8''%EC%A0%9C%EC%A3%BC%20%EB%B0%94%EB%8B%A4.jpg",
                "image/jpeg", PRESIGNED_GET_TTL);
        }

        @Test
        void 파일명의_큰따옴표는_폴백에서_작은따옴표로_바꾼다() throws Exception {
            Long mediaId = readyMedia("my\"pic.jpg");

            download(mediaId, hostToken).andExpect(status().isFound());

            verify(fileStorage).presignGet(storageKeyOf(mediaId),
                "attachment; filename=\"my'pic.jpg\"; filename*=UTF-8''my%22pic.jpg",
                "image/jpeg", PRESIGNED_GET_TTL);
        }

        @Test
        void 썸네일이_아직_없는_PROCESSING_미디어도_원본을_받는다() throws Exception {
            Long mediaId = media(roomId, hostId, "a.jpg", UploadStatus.PROCESSING);

            download(mediaId, hostToken).andExpect(status().isFound());
        }

        @Test
        void 다른_사람이_올린_미디어도_방_멤버면_받는다() throws Exception {
            Long mediaId = readyMedia("a.jpg");

            download(mediaId, guestToken).andExpect(status().isFound());
        }

        @ParameterizedTest
        @EnumSource(value = UploadStatus.class, names = {"RESERVED", "FAILED"})
        void 실물이_없는_상태면_404(UploadStatus status) throws Exception {
            Long mediaId = media(roomId, hostId, "a.jpg", status);

            download(mediaId, hostToken)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEDIA_NOT_FOUND"));
            verify(fileStorage, never()).presignGet(any(), anyString(), anyString(), any());
        }

        @Test
        void 없는_미디어면_404() throws Exception {
            download(Long.MAX_VALUE, hostToken)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEDIA_NOT_FOUND"));
        }

        @Test
        void 삭제된_미디어면_404() throws Exception {
            Long mediaId = readyMedia("a.jpg");
            deleteMediaService.deleteOne(roomId, mediaId, hostId);

            download(mediaId, hostToken)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEDIA_NOT_FOUND"));
        }

        @Test
        void 다른_방_미디어면_404() throws Exception {
            Long otherRoomMedia = media(otherRoomId, hostId, "a.jpg", UploadStatus.READY);

            download(otherRoomMedia, hostToken)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEDIA_NOT_FOUND"));
        }

        @Test
        void 방에_입장하지_않은_사용자는_403() throws Exception {
            Long mediaId = readyMedia("a.jpg");
            AuthResult outsider = anonymousAuthService.authenticate("외부인");

            download(mediaId, "Bearer " + outsider.accessToken())
                .andExpect(status().isForbidden());
        }

        @Test
        void 인증_없이_요청하면_401() throws Exception {
            Long mediaId = readyMedia("a.jpg");

            mockMvc.perform(get("/api/v1/rooms/{roomId}/downloads/media/{mediaId}", roomId, mediaId))
                .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    class 다건_다운로드 {

        private ResultActions batch(String body, String token) throws Exception {
            return mockMvc.perform(post("/api/v1/rooms/{roomId}/downloads/batch", roomId)
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
        }

        private ResultActions batch(String body) throws Exception {
            return batch(body, hostToken);
        }

        private String mediaIdsBody(Long... mediaIds) {
            return "{\"mediaIds\":[%s]}".formatted(
                java.util.Arrays.stream(mediaIds).map(String::valueOf).collect(Collectors.joining(",")));
        }

        private JsonNode files(ResultActions result) throws Exception {
            return objectMapper.readTree(result.andReturn().getResponse().getContentAsString())
                .path("data").path("files");
        }

        private List<Long> mediaIdsOf(JsonNode files) {
            return java.util.stream.StreamSupport.stream(files.spliterator(), false)
                .map(file -> file.path("mediaId").asLong()).toList();
        }

        @Test
        void 요청_순서대로_파일마다_파일명과_서명_URL이_대응한다() throws Exception {
            Long first = readyMedia("b.jpg");
            Long second = readyMedia("제주 바다.jpg");
            Long third = readyMedia("a.jpg");
            Instant before = Instant.now();

            JsonNode files = files(batch(mediaIdsBody(second, third, first)).andExpect(status().isOk()));

            assertThat(mediaIdsOf(files)).containsExactly(second, third, first);
            assertThat(files.get(0).path("fileName").asText()).isEqualTo("제주 바다.jpg");
            assertThat(files.get(0).path("downloadUrl").asText()).isEqualTo(signedUrl(storageKeyOf(second),
                "attachment; filename=\"download.jpg\"; filename*=UTF-8''%EC%A0%9C%EC%A3%BC%20%EB%B0%94%EB%8B%A4.jpg"));
            assertThat(files.get(1).path("fileName").asText()).isEqualTo("a.jpg");
            assertThat(files.get(1).path("downloadUrl").asText()).isEqualTo(signedUrl(storageKeyOf(third),
                "attachment; filename=\"a.jpg\"; filename*=UTF-8''a.jpg"));
            assertThat(files.get(2).path("fileName").asText()).isEqualTo("b.jpg");
            assertThat(files.get(2).path("downloadUrl").asText()).isEqualTo(signedUrl(storageKeyOf(first),
                "attachment; filename=\"b.jpg\"; filename*=UTF-8''b.jpg"));
            verify(fileStorage).presignGet(storageKeyOf(first),
                "attachment; filename=\"b.jpg\"; filename*=UTF-8''b.jpg", "image/jpeg", PRESIGNED_GET_TTL);

            Instant expiresAt = Instant.parse(files.get(0).path("expiresAt").asText());
            assertThat(expiresAt).isBetween(before.plus(PRESIGNED_GET_TTL), Instant.now().plus(PRESIGNED_GET_TTL));
            assertThat(files.findValuesAsText("expiresAt")).containsOnly(files.get(0).path("expiresAt").asText());
        }

        @Test
        void 같은_원본_파일명은_번호를_붙이고_그_이름으로_서명한다() throws Exception {
            Long first = readyMedia("a.jpg");
            Long second = readyMedia("a.jpg");
            Long third = readyMedia("a.jpg");

            JsonNode files = files(batch(mediaIdsBody(first, second, third)).andExpect(status().isOk()));

            assertThat(files.findValuesAsText("fileName")).containsExactly("a.jpg", "a (1).jpg", "a (2).jpg");
            assertThat(files.get(2).path("downloadUrl").asText()).isEqualTo(signedUrl(storageKeyOf(third),
                "attachment; filename=\"a (2).jpg\"; filename*=UTF-8''a%20%282%29.jpg"));
        }

        @Test
        void 같은_ID를_여러_번_보내도_한_번만_받는다() throws Exception {
            Long mediaId = readyMedia("a.jpg");

            JsonNode files = files(batch(mediaIdsBody(mediaId, mediaId)).andExpect(status().isOk()));

            assertThat(mediaIdsOf(files)).containsExactly(mediaId);
        }

        @Test
        void 다른_방_없는_ID_실물이_없는_미디어는_조용히_뺀다() throws Exception {
            Long ready = readyMedia("a.jpg");
            Long processing = media(roomId, hostId, "b.jpg", UploadStatus.PROCESSING);
            Long reserved = media(roomId, hostId, "c.jpg", UploadStatus.RESERVED);
            Long failed = media(roomId, hostId, "d.jpg", UploadStatus.FAILED);
            Long otherRoom = media(otherRoomId, hostId, "e.jpg", UploadStatus.READY);

            JsonNode files = files(batch(mediaIdsBody(ready, reserved, otherRoom, Long.MAX_VALUE, failed, processing))
                .andExpect(status().isOk()));

            assertThat(mediaIdsOf(files)).containsExactly(ready, processing);
        }

        @Test
        void 남은_대상이_없으면_404() throws Exception {
            Long reserved = media(roomId, hostId, "a.jpg", UploadStatus.RESERVED);
            Long otherRoom = media(otherRoomId, hostId, "b.jpg", UploadStatus.READY);

            batch(mediaIdsBody(reserved, otherRoom, Long.MAX_VALUE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEDIA_NOT_FOUND"));
        }

        @Test
        void 요청_ID가_1000개를_넘으면_400() throws Exception {
            String body = "{\"mediaIds\":[%s]}".formatted(LongStream.rangeClosed(1, 1001)
                .mapToObj(String::valueOf).collect(Collectors.joining(",")));

            batch(body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TOO_MANY_FILES"));
        }

        @Test
        void 폴더_전체를_받으면_폴더의_다운로드_가능한_미디어만_준다() throws Exception {
            Long ready = readyMedia("a.jpg");
            Long reserved = media(roomId, hostId, "b.jpg", UploadStatus.RESERVED);
            Long outside = readyMedia("c.jpg");
            Long folderId = folderWith("1일차", ready, reserved);

            JsonNode files = files(batch("{\"folderId\":%d}".formatted(folderId)).andExpect(status().isOk()));

            assertThat(mediaIdsOf(files)).containsExactly(ready).doesNotContain(outside);
            assertThat(files.get(0).path("downloadUrl").asText()).isEqualTo(signedUrl(storageKeyOf(ready),
                "attachment; filename=\"a.jpg\"; filename*=UTF-8''a.jpg"));
        }

        @Test
        void 폴더_전체에_업로더_필터를_적용한다() throws Exception {
            Long mine = media(roomId, hostId, "mine.jpg", UploadStatus.READY);
            Long others = media(roomId, guestId, "others.jpg", UploadStatus.READY);
            Long folderId = folderWith("1일차", mine, others);

            JsonNode me = files(batch("{\"folderId\":%d,\"uploader\":\"ME\"}".formatted(folderId))
                .andExpect(status().isOk()));
            JsonNode notMe = files(batch("{\"folderId\":%d,\"uploader\":\"OTHERS\"}".formatted(folderId))
                .andExpect(status().isOk()));
            JsonNode all = files(batch("{\"folderId\":%d,\"uploader\":\"ALL\"}".formatted(folderId))
                .andExpect(status().isOk()));

            assertThat(mediaIdsOf(me)).containsExactly(mine);
            assertThat(mediaIdsOf(notMe)).containsExactly(others);
            assertThat(mediaIdsOf(all)).containsExactlyInAnyOrder(mine, others);
        }

        @Test
        void 없는_폴더면_404() throws Exception {
            batch("{\"folderId\":%d}".formatted(Long.MAX_VALUE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FOLDER_NOT_FOUND"));
        }

        @Test
        void 다른_방_폴더면_404() throws Exception {
            Long otherRoomFolder = createFolderService.create(otherRoomId, "남의 폴더").getId();

            batch("{\"folderId\":%d}".formatted(otherRoomFolder))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FOLDER_NOT_FOUND"));
        }

        @ParameterizedTest
        @ValueSource(strings = {
            "{}",
            "{\"mediaIds\":[1],\"folderId\":1}",
            "{\"mediaIds\":[1],\"uploader\":\"ME\"}",
            "{\"uploader\":\"ME\"}"
        })
        void 대상_지정이_mediaIds_folderId_중_하나가_아니면_400(String body) throws Exception {
            batch(body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAM"));
            verify(fileStorage, never()).presignGet(any(), anyString(), anyString(), any());
        }

        @Test
        void 지원하지_않는_업로더_필터는_400() throws Exception {
            Long folderId = createFolderService.create(roomId, "1일차").getId();

            batch("{\"folderId\":%d,\"uploader\":\"EVERYONE\"}".formatted(folderId))
                .andExpect(status().isBadRequest());
        }

        @Test
        void 방에_입장하지_않은_사용자는_403() throws Exception {
            Long mediaId = readyMedia("a.jpg");
            AuthResult outsider = anonymousAuthService.authenticate("외부인");

            batch(mediaIdsBody(mediaId), "Bearer " + outsider.accessToken())
                .andExpect(status().isForbidden());
        }

        @Test
        void 인증_없이_요청하면_401() throws Exception {
            mockMvc.perform(post("/api/v1/rooms/{roomId}/downloads/batch", roomId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"mediaIds\":[1]}"))
                .andExpect(status().isUnauthorized());
        }
    }
}
