package com.sssok.renewal.incoming.download;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sssok.renewal.application.download.batch.BatchDownloadFile;
import com.sssok.renewal.application.download.batch.CreateBatchDownloadService;
import com.sssok.renewal.application.download.shared.DownloadTargetCommand;
import com.sssok.renewal.application.download.exception.InvalidDownloadParamException;
import com.sssok.renewal.application.download.exception.TooManyDownloadFilesException;
import com.sssok.renewal.application.download.exception.DownloadMediaNotFoundException;
import com.sssok.renewal.application.download.single.GetMediaDownloadUrlService;
import com.sssok.application.media.MediaUploaderFilter;
import com.sssok.application.port.out.RoomMemberRepository;
import com.sssok.application.port.out.RoomRepository;
import com.sssok.application.port.out.AdminTokenProvider;
import com.sssok.application.port.out.TokenProvider;
import com.sssok.domain.room.Room;
import com.sssok.domain.room.RoomCode;
import com.sssok.domain.room.RoomMember;
import com.sssok.domain.room.RoomName;
import com.sssok.domain.room.UploadPolicy;
import com.sssok.domain.room.roomstatus.RoomStatus;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@WebMvcTest(DownloadController.class)
class DownloadControllerTest {

    private static final Long ROOM_ID = 10L;
    private static final Long MEMBER_ID = 2L;
    private static final Long MEDIA_ID = 5012L;
    private static final String BEARER = "Bearer valid-token";

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    GetMediaDownloadUrlService getMediaDownloadUrlService;

    @MockitoBean
    CreateBatchDownloadService createBatchDownloadService;

    @MockitoBean
    TokenProvider tokenProvider;

    @MockitoBean
    AdminTokenProvider adminTokenProvider;

    @MockitoBean
    RoomRepository roomRepository;

    @MockitoBean
    RoomMemberRepository roomMemberRepository;

    @BeforeEach
    void setUp() {
        given(tokenProvider.parse("valid-token")).willReturn(MEMBER_ID);
        given(roomRepository.findById(ROOM_ID)).willReturn(Optional.of(activeRoom()));
        given(roomMemberRepository.findByRoomIdAndMemberId(ROOM_ID, MEMBER_ID))
            .willReturn(Optional.of(RoomMember.reconstruct(1L, ROOM_ID, MEMBER_ID, Instant.now())));
    }

    private Room activeRoom() {
        return Room.reconstruct(ROOM_ID, null, RoomCode.generate(new SecureRandom()),
            new RoomName("우테코 회식"), RoomStatus.initial(),
            UploadPolicy.ANYONE,
            1L, Instant.now(), null);
    }

    // --- 단건 다운로드: GET /downloads/media/{mediaId} ---

    private ResultActions downloadMedia() throws Exception {
        return mockMvc.perform(get("/api/v1/rooms/{roomId}/downloads/media/{mediaId}", ROOM_ID, MEDIA_ID)
            .header("Authorization", BEARER));
    }

    @Test
    void 단건_다운로드하면_302와_Location_헤더를_반환한다() throws Exception {
        given(getMediaDownloadUrlService.getUrl(anyLong(), anyLong()))
            .willReturn("https://storage.example.com/signed");

        downloadMedia()
            .andExpect(status().isFound())
            .andExpect(header().string("Location", "https://storage.example.com/signed"));
    }

    @Test
    void 단건_다운로드에서_없는_미디어면_404() throws Exception {
        given(getMediaDownloadUrlService.getUrl(anyLong(), anyLong()))
            .willThrow(new DownloadMediaNotFoundException());

        downloadMedia()
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("MEDIA_NOT_FOUND"));
    }

    @Test
    void 단건_다운로드도_인증_없이_요청하면_401() throws Exception {
        mockMvc.perform(get("/api/v1/rooms/{roomId}/downloads/media/{mediaId}", ROOM_ID, MEDIA_ID))
            .andExpect(status().isUnauthorized());
    }

    // --- 다건 다운로드: POST /downloads/batch ---


    // #275: 요청의 uploader 가 서비스까지 그대로 전달되는지 본다.
    @Test
    void 다건_다운로드의_업로더_필터를_전달한다() throws Exception {
        given(createBatchDownloadService.create(any())).willReturn(List.of());

        createBatch("{\"folderId\":31,\"uploader\":\"ME\"}")
            .andExpect(status().isOk());

        verify(createBatchDownloadService).create(
            new DownloadTargetCommand(ROOM_ID, MEMBER_ID, null, 31L, MediaUploaderFilter.ME));
    }

    @Test
    void 다건_다운로드에_업로더를_생략하면_null로_전달한다() throws Exception {
        given(createBatchDownloadService.create(any())).willReturn(List.of());

        createBatch("{\"mediaIds\":[5012]}")
            .andExpect(status().isOk());

        verify(createBatchDownloadService).create(
            new DownloadTargetCommand(ROOM_ID, MEMBER_ID, List.of(5012L), null, null));
    }

    @Test
    void 다건_다운로드의_지원하지_않는_업로더_필터는_400() throws Exception {
        createBatch("{\"mediaIds\":[5012],\"uploader\":\"UNKNOWN\"}")
            .andExpect(status().isBadRequest());
    }

    private ResultActions createBatch(String body) throws Exception {
        return mockMvc.perform(post("/api/v1/rooms/{roomId}/downloads/batch", ROOM_ID)
            .header("Authorization", BEARER)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
    }

    @Test
    void 다건_다운로드하면_200과_파일별_서명_URL_목록을_반환한다() throws Exception {
        Instant expiresAt = Instant.parse("2026-08-28T12:00:00Z");
        List<BatchDownloadFile> files = List.of(
            new BatchDownloadFile(5012L, "IMG_0421.jpg", "https://storage.example.com/5012", expiresAt),
            new BatchDownloadFile(5011L, "IMG_0420.jpg", "https://storage.example.com/5011", expiresAt));
        given(createBatchDownloadService.create(any())).willReturn(files);

        createBatch("{\"mediaIds\":[5012,5011]}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.files.length()").value(2))
            .andExpect(jsonPath("$.data.files[0].mediaId").value(5012))
            .andExpect(jsonPath("$.data.files[0].fileName").value("IMG_0421.jpg"))
            .andExpect(jsonPath("$.data.files[0].downloadUrl").value("https://storage.example.com/5012"));
    }

    @Test
    void 다건_다운로드에서_mediaIds와_folderId를_함께_보내면_400() throws Exception {
        given(createBatchDownloadService.create(any()))
            .willThrow(InvalidDownloadParamException.invalidTarget());

        createBatch("{\"mediaIds\":[1],\"folderId\":2}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_PARAM"));
    }

    @Test
    void 다건_다운로드에서_개수가_상한을_초과하면_400() throws Exception {
        given(createBatchDownloadService.create(any()))
            .willThrow(new TooManyDownloadFilesException(1000));

        createBatch("{\"mediaIds\":[1]}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("TOO_MANY_FILES"));
    }

    @Test
    void 다건_다운로드에서_대상_미디어가_없으면_404() throws Exception {
        given(createBatchDownloadService.create(any()))
            .willThrow(new DownloadMediaNotFoundException());

        createBatch("{\"mediaIds\":[999]}")
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("MEDIA_NOT_FOUND"));
    }

    @Test
    void 다건_다운로드도_인증_없이_요청하면_401() throws Exception {
        mockMvc.perform(post("/api/v1/rooms/{roomId}/downloads/batch", ROOM_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"mediaIds\":[1]}"))
            .andExpect(status().isUnauthorized());
    }
}
