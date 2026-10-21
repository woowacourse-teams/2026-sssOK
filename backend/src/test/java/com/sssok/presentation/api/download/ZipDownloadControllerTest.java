package com.sssok.presentation.api.download;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sssok.application.download.CreateDownloadJobResult;
import com.sssok.application.download.CreateDownloadJobService;
import com.sssok.application.download.GetDownloadJobStatusResult;
import com.sssok.application.download.GetDownloadJobStatusService;
import com.sssok.application.download.exception.DownloadExpiredException;
import com.sssok.application.download.exception.DownloadForbiddenException;
import com.sssok.application.download.exception.DownloadNotFoundException;
import com.sssok.application.download.exception.DownloadRateLimitedException;
import com.sssok.renewal.application.download.exception.InvalidDownloadParamException;
import com.sssok.renewal.application.download.exception.TooManyDownloadFilesException;
import com.sssok.renewal.application.download.exception.DownloadMediaNotFoundException;
import com.sssok.application.media.MediaUploaderFilter;
import com.sssok.application.port.out.RoomMemberRepository;
import com.sssok.application.port.out.RoomRepository;
import com.sssok.application.port.out.AdminTokenProvider;
import com.sssok.application.port.out.TokenProvider;
import com.sssok.domain.download.DownloadJobStatus;
import com.sssok.domain.room.Room;
import com.sssok.domain.room.RoomCode;
import com.sssok.domain.room.RoomMember;
import com.sssok.domain.room.RoomName;
import com.sssok.domain.room.UploadPolicy;
import com.sssok.domain.room.roomstatus.RoomStatus;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@WebMvcTest(ZipDownloadController.class)
class ZipDownloadControllerTest {

    private static final Long ROOM_ID = 10L;
    private static final Long MEMBER_ID = 2L;
    private static final Long MEDIA_ID = 5012L;
    private static final String BEARER = "Bearer valid-token";

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    CreateDownloadJobService createDownloadJobService;

    @MockitoBean
    GetDownloadJobStatusService getDownloadJobStatusService;

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

    @Test
    void zip_요청의_업로더_필터를_전달한다() throws Exception {
        CreateDownloadJobResult result =
            new CreateDownloadJobResult(1L, DownloadJobStatus.QUEUED, 1, 100L, "sssOK_10.zip");
        given(createDownloadJobService.create(anyLong(), anyLong(), any(), nullable(Long.class),
            nullable(MediaUploaderFilter.class))).willReturn(result);

        createZip("{\"folderId\":31,\"uploader\":\"OTHERS\"}")
            .andExpect(status().isAccepted());

        verify(createDownloadJobService).create(eq(ROOM_ID), anyLong(), any(),
            nullable(Long.class), eq(MediaUploaderFilter.OTHERS));
    }

    @Test
    void zip_요청의_지원하지_않는_업로더_필터는_400() throws Exception {
        createZip("{\"mediaIds\":[5012],\"uploader\":\"UNKNOWN\"}")
            .andExpect(status().isBadRequest());
    }

    // --- zip 다운로드: POST /downloads/zip, GET /downloads/zip/{jobId} ---

    private ResultActions createZip(String body) throws Exception {
        return mockMvc.perform(post("/api/v1/rooms/{roomId}/downloads/zip", ROOM_ID)
            .header("Authorization", BEARER)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
    }

    @Test
    void 압축_요청하면_202와_잡_정보를_반환한다() throws Exception {
        CreateDownloadJobResult result =
            new CreateDownloadJobResult(1L, DownloadJobStatus.QUEUED, 3, 741843619L, "sssOK_10.zip");
        given(createDownloadJobService.create(anyLong(), anyLong(), any(), nullable(Long.class),
            nullable(MediaUploaderFilter.class))).willReturn(result);

        createZip("{\"mediaIds\":[5012,5011,5008]}")
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.data.jobId").value(1))
            .andExpect(jsonPath("$.data.status").value("QUEUED"))
            .andExpect(jsonPath("$.data.mediaCount").value(3))
            .andExpect(jsonPath("$.data.totalSize").value(741843619))
            .andExpect(jsonPath("$.data.fileName").value("sssOK_10.zip"));
    }

    @Test
    void zip_요청에서_mediaIds와_folderId를_함께_보내면_400() throws Exception {
        given(createDownloadJobService.create(anyLong(), anyLong(), any(), nullable(Long.class),
            nullable(MediaUploaderFilter.class)))
            .willThrow(InvalidDownloadParamException.invalidTarget());

        createZip("{\"mediaIds\":[1],\"folderId\":2}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_PARAM"));
    }

    @Test
    void zip_요청에서_개수가_상한을_초과하면_400() throws Exception {
        given(createDownloadJobService.create(anyLong(), anyLong(), any(), nullable(Long.class),
            nullable(MediaUploaderFilter.class)))
            .willThrow(new TooManyDownloadFilesException(1000));

        createZip("{\"mediaIds\":[1]}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("TOO_MANY_FILES"));
    }

    @Test
    void zip_요청에서_대상_미디어가_없으면_404() throws Exception {
        given(createDownloadJobService.create(anyLong(), anyLong(), any(), nullable(Long.class),
            nullable(MediaUploaderFilter.class)))
            .willThrow(new DownloadMediaNotFoundException());

        createZip("{\"mediaIds\":[999]}")
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("MEDIA_NOT_FOUND"));
    }

    @Test
    void zip_요청에서_동시_진행_중인_잡이_많으면_429() throws Exception {
        given(createDownloadJobService.create(anyLong(), anyLong(), any(), nullable(Long.class),
            nullable(MediaUploaderFilter.class)))
            .willThrow(new DownloadRateLimitedException());

        createZip("{\"mediaIds\":[1]}")
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
    }

    @Test
    void zip_요청도_인증_없이_요청하면_401() throws Exception {
        mockMvc.perform(post("/api/v1/rooms/{roomId}/downloads/zip", ROOM_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"mediaIds\":[1]}"))
            .andExpect(status().isUnauthorized());
    }

    private ResultActions getZipStatus(Long jobId) throws Exception {
        return mockMvc.perform(get("/api/v1/rooms/{roomId}/downloads/zip/{jobId}", ROOM_ID, jobId)
            .header("Authorization", BEARER));
    }

    @Test
    void zip_상태_조회하면_200과_잡_상태를_반환한다() throws Exception {
        Instant expiresAt = Instant.parse("2026-08-27T12:00:00Z");
        GetDownloadJobStatusResult result = new GetDownloadJobStatusResult(
            1L, DownloadJobStatus.READY, 100, 3, "sssOK_10.zip",
            "https://storage.example.com/zip-signed", expiresAt, null);
        given(getDownloadJobStatusService.getStatus(anyLong(), anyLong())).willReturn(result);

        getZipStatus(1L)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.jobId").value(1))
            .andExpect(jsonPath("$.data.status").value("READY"))
            .andExpect(jsonPath("$.data.progress").value(100))
            .andExpect(jsonPath("$.data.downloadUrl").value("https://storage.example.com/zip-signed"));
    }

    @Test
    void zip_상태_조회에서_본인이_아닌_잡을_조회하면_403() throws Exception {
        given(getDownloadJobStatusService.getStatus(anyLong(), anyLong()))
            .willThrow(new DownloadForbiddenException());

        getZipStatus(1L)
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("DOWNLOAD_FORBIDDEN"));
    }

    @Test
    void zip_상태_조회에서_없는_잡을_조회하면_404() throws Exception {
        given(getDownloadJobStatusService.getStatus(anyLong(), anyLong()))
            .willThrow(new DownloadNotFoundException());

        getZipStatus(999L)
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("DOWNLOAD_NOT_FOUND"));
    }

    @Test
    void zip_상태_조회에서_보관_기간이_지난_잡을_조회하면_410() throws Exception {
        given(getDownloadJobStatusService.getStatus(anyLong(), anyLong()))
            .willThrow(new DownloadExpiredException());

        getZipStatus(1L)
            .andExpect(status().isGone())
            .andExpect(jsonPath("$.code").value("DOWNLOAD_EXPIRED"));
    }

    @Test
    void zip_상태_조회도_인증_없이_요청하면_401() throws Exception {
        mockMvc.perform(get("/api/v1/rooms/{roomId}/downloads/zip/{jobId}", ROOM_ID, 1L))
            .andExpect(status().isUnauthorized());
    }
}
