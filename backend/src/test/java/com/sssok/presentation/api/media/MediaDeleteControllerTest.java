package com.sssok.presentation.api.media;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sssok.application.media.DeleteMediaResult;
import com.sssok.application.media.DeleteMediaService;
import com.sssok.application.media.exception.InvalidMediaDeleteParamException;
import com.sssok.application.media.exception.MediaForbiddenException;
import com.sssok.application.media.exception.MediaNotFoundException;
import com.sssok.application.media.exception.TooManyMediaException;
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
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(MediaDeleteController.class)
class MediaDeleteControllerTest {

    private static final Long ROOM_ID = 10L;
    private static final Long MEMBER_ID = 2L;
    private static final Long MEDIA_ID = 5012L;
    private static final String BEARER = "Bearer valid-token";

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    DeleteMediaService deleteMediaService;

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

    @Test
    void 단건을_삭제하면_삭제한_ID를_반환한다() throws Exception {
        given(deleteMediaService.deleteOne(ROOM_ID, MEDIA_ID, MEMBER_ID)).willReturn(MEDIA_ID);

        mockMvc.perform(delete("/api/v1/rooms/{roomId}/media/{mediaId}", ROOM_ID, MEDIA_ID)
                .header("Authorization", BEARER))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.deletedMediaId").value(MEDIA_ID));
    }

    @Test
    void 다건을_삭제하면_삭제와_미존재_ID를_나눠_반환한다() throws Exception {
        given(deleteMediaService.deleteAll(ROOM_ID, List.of(1L, 2L, 999L), MEMBER_ID))
            .willReturn(new DeleteMediaResult(2, List.of(1L, 2L), List.of(999L)));

        mockMvc.perform(delete("/api/v1/rooms/{roomId}/media", ROOM_ID)
                .header("Authorization", BEARER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"mediaIds\":[1,2,999]}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.deletedCount").value(2))
            .andExpect(jsonPath("$.data.deletedMediaIds[0]").value(1))
            .andExpect(jsonPath("$.data.notFoundMediaIds[0]").value(999));
    }

    @Test
    void 빈_목록이면_400_INVALID_PARAM() throws Exception {
        willThrow(new InvalidMediaDeleteParamException())
            .given(deleteMediaService).deleteAll(ROOM_ID, List.of(), MEMBER_ID);

        mockMvc.perform(delete("/api/v1/rooms/{roomId}/media", ROOM_ID)
                .header("Authorization", BEARER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"mediaIds\":[]}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_PARAM"));
    }

    @Test
    void mediaIds를_누락하면_400_INVALID_PARAM() throws Exception {
        willThrow(new InvalidMediaDeleteParamException())
            .given(deleteMediaService).deleteAll(ROOM_ID, null, MEMBER_ID);

        mockMvc.perform(delete("/api/v1/rooms/{roomId}/media", ROOM_ID)
                .header("Authorization", BEARER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_PARAM"));
    }

    @Test
    void 최대_개수를_넘으면_400_TOO_MANY_FILES() throws Exception {
        willThrow(new TooManyMediaException(500))
            .given(deleteMediaService).deleteAll(org.mockito.ArgumentMatchers.eq(ROOM_ID),
                org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.eq(MEMBER_ID));

        mockMvc.perform(delete("/api/v1/rooms/{roomId}/media", ROOM_ID)
                .header("Authorization", BEARER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"mediaIds\":[1]}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("TOO_MANY_FILES"));
    }

    @Test
    void 없는_미디어면_404_MEDIA_NOT_FOUND() throws Exception {
        willThrow(new MediaNotFoundException())
            .given(deleteMediaService).deleteOne(ROOM_ID, MEDIA_ID, MEMBER_ID);

        mockMvc.perform(delete("/api/v1/rooms/{roomId}/media/{mediaId}", ROOM_ID, MEDIA_ID)
                .header("Authorization", BEARER))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("MEDIA_NOT_FOUND"));
    }

    @Test
    void 삭제_권한이_없으면_403_MEDIA_FORBIDDEN() throws Exception {
        willThrow(new MediaForbiddenException())
            .given(deleteMediaService).deleteOne(ROOM_ID, MEDIA_ID, MEMBER_ID);

        mockMvc.perform(delete("/api/v1/rooms/{roomId}/media/{mediaId}", ROOM_ID, MEDIA_ID)
                .header("Authorization", BEARER))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("MEDIA_FORBIDDEN"));
    }

    private Room activeRoom() {
        return Room.reconstruct(ROOM_ID, null, RoomCode.generate(new SecureRandom()),
            new RoomName("우테코 회식"), RoomStatus.initial(),
            new RoomExpiration(Instant.now().plusSeconds(3600)), UploadPolicy.ANYONE,
            1L, Instant.now(), null);
    }
}
