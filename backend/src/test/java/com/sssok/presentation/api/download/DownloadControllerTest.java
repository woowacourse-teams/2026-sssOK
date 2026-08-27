package com.sssok.presentation.api.download;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sssok.application.download.CreateDownloadJobResult;
import com.sssok.application.download.CreateDownloadJobService;
import com.sssok.application.download.exception.DownloadRateLimitedException;
import com.sssok.application.download.exception.InvalidDownloadParamException;
import com.sssok.application.download.exception.TooManyFilesException;
import com.sssok.application.media.exception.MediaNotFoundException;
import com.sssok.application.port.out.RoomMemberRepository;
import com.sssok.application.port.out.RoomRepository;
import com.sssok.application.port.out.TokenProvider;
import com.sssok.domain.download.DownloadJobStatus;
import com.sssok.domain.room.Room;
import com.sssok.domain.room.RoomCode;
import com.sssok.domain.room.RoomExpiration;
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

@WebMvcTest(DownloadController.class)
class DownloadControllerTest {

    private static final Long ROOM_ID = 10L;
    private static final Long MEMBER_ID = 2L;
    private static final String BEARER = "Bearer valid-token";

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    CreateDownloadJobService createDownloadJobService;

    @MockitoBean
    TokenProvider tokenProvider;

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
            new RoomExpiration(Instant.now().plusSeconds(3600)), UploadPolicy.ANYONE,
            1L, Instant.now(), null);
    }

    private ResultActions createJob(String body) throws Exception {
        return mockMvc.perform(post("/api/v1/rooms/{roomId}/downloads", ROOM_ID)
            .header("Authorization", BEARER)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
    }

    @Test
    void 압축_요청하면_202와_잡_정보를_반환한다() throws Exception {
        CreateDownloadJobResult result =
            new CreateDownloadJobResult(1L, DownloadJobStatus.QUEUED, 3, 741843619L, "sssOK_10.zip");
        given(createDownloadJobService.create(anyLong(), anyLong(), any(), any())).willReturn(result);

        createJob("{\"mediaIds\":[5012,5011,5008]}")
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.data.jobId").value(1))
            .andExpect(jsonPath("$.data.status").value("QUEUED"))
            .andExpect(jsonPath("$.data.mediaCount").value(3))
            .andExpect(jsonPath("$.data.totalSize").value(741843619))
            .andExpect(jsonPath("$.data.fileName").value("sssOK_10.zip"));
    }

    @Test
    void mediaIds와_folderId를_함께_보내면_400() throws Exception {
        given(createDownloadJobService.create(anyLong(), anyLong(), any(), any()))
            .willThrow(new InvalidDownloadParamException());

        createJob("{\"mediaIds\":[1],\"folderId\":2}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_PARAM"));
    }

    @Test
    void 개수가_상한을_초과하면_400() throws Exception {
        given(createDownloadJobService.create(anyLong(), anyLong(), any(), any()))
            .willThrow(new TooManyFilesException(1000));

        createJob("{\"mediaIds\":[1]}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("TOO_MANY_FILES"));
    }

    @Test
    void 대상_미디어가_없으면_404() throws Exception {
        given(createDownloadJobService.create(anyLong(), anyLong(), any(), any()))
            .willThrow(new MediaNotFoundException());

        createJob("{\"mediaIds\":[999]}")
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("MEDIA_NOT_FOUND"));
    }

    @Test
    void 동시_진행_중인_잡이_많으면_429() throws Exception {
        given(createDownloadJobService.create(anyLong(), anyLong(), any(), any()))
            .willThrow(new DownloadRateLimitedException());

        createJob("{\"mediaIds\":[1]}")
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
    }

    @Test
    void 인증_없이_요청하면_401() throws Exception {
        mockMvc.perform(post("/api/v1/rooms/{roomId}/downloads", ROOM_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"mediaIds\":[1]}"))
            .andExpect(status().isUnauthorized());
    }
}
