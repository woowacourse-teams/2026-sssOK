package com.sssok.presentation.api.medialike;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sssok.application.media.exception.MediaNotFoundException;
import com.sssok.application.medialike.LikeMediaService;
import com.sssok.application.medialike.MediaLikeResult;
import com.sssok.application.medialike.UnlikeMediaService;
import com.sssok.application.port.out.AdminTokenProvider;
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

@WebMvcTest(MediaLikeController.class)
class MediaLikeControllerTest {

    private static final Long ROOM_ID = 10L;
    private static final Long MEDIA_ID = 5012L;
    private static final Long MEMBER_ID = 2L;
    private static final String BEARER = "Bearer valid-token";

    @Autowired MockMvc mockMvc;
    @MockitoBean LikeMediaService likeMediaService;
    @MockitoBean UnlikeMediaService unlikeMediaService;
    @MockitoBean TokenProvider tokenProvider;
    @MockitoBean AdminTokenProvider adminTokenProvider;
    @MockitoBean RoomRepository roomRepository;
    @MockitoBean RoomMemberRepository roomMemberRepository;

    @BeforeEach
    void setUp() {
        given(tokenProvider.parse("valid-token")).willReturn(MEMBER_ID);
        given(roomRepository.findById(ROOM_ID)).willReturn(Optional.of(roomExpiringAt(Instant.now().plusSeconds(3600))));
        given(roomMemberRepository.findByRoomIdAndMemberId(ROOM_ID, MEMBER_ID))
            .willReturn(Optional.of(RoomMember.reconstruct(1L, ROOM_ID, MEMBER_ID, Instant.now())));
    }

    @Test
    void 좋아요를_누르면_200과_변경_후_상태를_반환한다() throws Exception {
        given(likeMediaService.like(ROOM_ID, MEDIA_ID, MEMBER_ID))
            .willReturn(new MediaLikeResult(MEDIA_ID, true, 3));

        mockMvc.perform(put("/api/v1/rooms/{roomId}/media/{mediaId}/likes", ROOM_ID, MEDIA_ID)
                .header("Authorization", BEARER))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.mediaId").value(MEDIA_ID))
            .andExpect(jsonPath("$.data.liked").value(true))
            .andExpect(jsonPath("$.data.likeCount").value(3));
        verify(likeMediaService).like(ROOM_ID, MEDIA_ID, MEMBER_ID);
    }

    @Test
    void 좋아요를_취소하면_200과_변경_후_상태를_반환한다() throws Exception {
        given(unlikeMediaService.unlike(ROOM_ID, MEDIA_ID, MEMBER_ID))
            .willReturn(new MediaLikeResult(MEDIA_ID, false, 2));

        mockMvc.perform(delete("/api/v1/rooms/{roomId}/media/{mediaId}/likes", ROOM_ID, MEDIA_ID)
                .header("Authorization", BEARER))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.liked").value(false))
            .andExpect(jsonPath("$.data.likeCount").value(2));
    }

    @Test
    void 인증이_없으면_401을_반환한다() throws Exception {
        mockMvc.perform(put("/api/v1/rooms/{roomId}/media/{mediaId}/likes", ROOM_ID, MEDIA_ID))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void 입장하지_않았으면_403을_반환한다() throws Exception {
        given(roomMemberRepository.findByRoomIdAndMemberId(ROOM_ID, MEMBER_ID)).willReturn(Optional.empty());

        mockMvc.perform(put("/api/v1/rooms/{roomId}/media/{mediaId}/likes", ROOM_ID, MEDIA_ID)
                .header("Authorization", BEARER))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("NOT_ROOM_MEMBER"));
    }

    @Test
    void 만료된_방이면_410을_반환한다() throws Exception {
        given(roomRepository.findById(ROOM_ID)).willReturn(Optional.of(roomExpiringAt(Instant.now().minusSeconds(1))));

        mockMvc.perform(delete("/api/v1/rooms/{roomId}/media/{mediaId}/likes", ROOM_ID, MEDIA_ID)
                .header("Authorization", BEARER))
            .andExpect(status().isGone())
            .andExpect(jsonPath("$.code").value("ROOM_EXPIRED"));
    }

    @Test
    void 없는_미디어면_404를_반환한다() throws Exception {
        given(likeMediaService.like(ROOM_ID, MEDIA_ID, MEMBER_ID)).willThrow(new MediaNotFoundException());

        mockMvc.perform(put("/api/v1/rooms/{roomId}/media/{mediaId}/likes", ROOM_ID, MEDIA_ID)
                .header("Authorization", BEARER))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("MEDIA_NOT_FOUND"));
    }

    private Room roomExpiringAt(Instant expiresAt) {
        return Room.reconstruct(ROOM_ID, null, RoomCode.generate(new SecureRandom()),
            new RoomName("우테코 회식"), RoomStatus.initial(),
            new RoomExpiration(expiresAt), UploadPolicy.ANYONE,
            1L, Instant.now(), null);
    }
}
