package com.sssok.presentation.api.media;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sssok.application.media.GetMediaDownloadUrlService;
import com.sssok.application.media.GetOriginalUrlService;
import com.sssok.application.media.GetThumbnailUrlService;
import com.sssok.application.media.exception.MediaNotFoundException;
import com.sssok.application.media.exception.MediaNotReadyException;
import com.sssok.application.port.out.RoomMemberRepository;
import com.sssok.application.port.out.RoomRepository;
import com.sssok.application.port.out.TokenProvider;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@WebMvcTest(MediaDownloadController.class)
class MediaDownloadControllerTest {

    private static final Long ROOM_ID = 10L;
    private static final Long MEMBER_ID = 2L;
    private static final Long MEDIA_ID = 5012L;
    private static final String BEARER = "Bearer valid-token";

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    GetMediaDownloadUrlService getMediaDownloadUrlService;

    @MockitoBean
    GetThumbnailUrlService getThumbnailUrlService;

    @MockitoBean
    GetOriginalUrlService getOriginalUrlService;

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

    private ResultActions download() throws Exception {
        return mockMvc.perform(get("/api/v1/rooms/{roomId}/media/{mediaId}/download", ROOM_ID, MEDIA_ID)
            .header("Authorization", BEARER));
    }

    @Test
    void 다운로드하면_302와_Location_헤더를_반환한다() throws Exception {
        given(getMediaDownloadUrlService.getUrl(anyLong(), anyLong()))
            .willReturn("https://storage.example.com/signed");

        download()
            .andExpect(status().isFound())
            .andExpect(header().string("Location", "https://storage.example.com/signed"));
    }

    @Test
    void 없는_미디어면_404() throws Exception {
        given(getMediaDownloadUrlService.getUrl(anyLong(), anyLong()))
            .willThrow(new MediaNotFoundException());

        download()
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("MEDIA_NOT_FOUND"));
    }

    @Test
    void 처리중인_미디어면_409() throws Exception {
        given(getMediaDownloadUrlService.getUrl(anyLong(), anyLong()))
            .willThrow(new MediaNotReadyException());

        download()
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("MEDIA_NOT_READY"));
    }

    @Test
    void 인증_없이_요청하면_401() throws Exception {
        mockMvc.perform(get("/api/v1/rooms/{roomId}/media/{mediaId}/download", ROOM_ID, MEDIA_ID))
            .andExpect(status().isUnauthorized());
    }
}
