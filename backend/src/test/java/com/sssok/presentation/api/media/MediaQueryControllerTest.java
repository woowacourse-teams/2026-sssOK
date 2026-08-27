package com.sssok.presentation.api.media;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sssok.application.folder.exception.FolderNotFoundException;
import com.sssok.application.media.GetMediaListService;
import com.sssok.application.media.GetMediaService;
import com.sssok.application.media.MediaDetail;
import com.sssok.application.media.MediaFullDetail;
import com.sssok.application.media.exception.MediaNotFoundException;
import com.sssok.application.port.out.RoomMemberRepository;
import com.sssok.application.port.out.RoomRepository;
import com.sssok.application.port.out.TokenProvider;
import com.sssok.domain.file.GeoPoint;
import com.sssok.domain.room.Room;
import com.sssok.domain.room.RoomCode;
import com.sssok.domain.room.RoomExpiration;
import com.sssok.domain.room.RoomMember;
import com.sssok.domain.room.RoomName;
import com.sssok.domain.room.UploadPolicy;
import com.sssok.domain.room.roomstatus.RoomStatus;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@WebMvcTest(MediaQueryController.class)
class MediaQueryControllerTest {

    private static final Long ROOM_ID = 10L;
    private static final Long MEMBER_ID = 2L;
    private static final Long MEDIA_ID = 5012L;
    private static final Long FOLDER_ID = 31L;
    private static final String BEARER = "Bearer valid-token";

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    GetMediaListService getMediaListService;

    @MockitoBean
    GetMediaService getMediaService;

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
    void 목록을_조회하면_200과_items_를_반환한다() throws Exception {
        given(getMediaListService.list(anyLong(), any())).willReturn(List.of(media()));

        getMediaList("")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[0].mediaId").value(MEDIA_ID))
            .andExpect(jsonPath("$.data.items[0].type").value("IMAGE"))
            .andExpect(jsonPath("$.data.items[0].fileName").value("사진.jpg"))
            .andExpect(jsonPath("$.data.items[0].mimeType").value("image/jpeg"))
            .andExpect(jsonPath("$.data.items[0].size").value(1024))
            .andExpect(jsonPath("$.data.items[0].status").value("READY"))
            .andExpect(jsonPath("$.data.items[0].uploaderId").value(7))
            .andExpect(jsonPath("$.data.items[0].uploaderName").value("가현"))
            .andExpect(jsonPath("$.data.items[0].folderIds[0]").value(FOLDER_ID))
            .andExpect(jsonPath("$.data.items[0].thumbnailUrl").doesNotExist())
            .andExpect(jsonPath("$.data.items[0].uploadedAt").exists());
    }

    @Test
    void 미디어가_없으면_빈_배열을_반환한다() throws Exception {
        given(getMediaListService.list(anyLong(), any())).willReturn(List.of());

        getMediaList("")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items").isArray())
            .andExpect(jsonPath("$.data.items").isEmpty());
    }

    @Test
    void folderId를_생략하면_null로_넘긴다() throws Exception {
        given(getMediaListService.list(anyLong(), any())).willReturn(List.of());

        getMediaList("");

        verify(getMediaListService).list(eq(ROOM_ID), isNull());
    }

    @Test
    void folderId를_주면_그대로_넘긴다() throws Exception {
        given(getMediaListService.list(anyLong(), any())).willReturn(List.of());

        getMediaList("?folderId=" + FOLDER_ID);

        verify(getMediaListService).list(ROOM_ID, FOLDER_ID);
    }

    @Test
    void 없는_폴더로_필터하면_404() throws Exception {
        willThrow(new FolderNotFoundException(FOLDER_ID))
            .given(getMediaListService).list(anyLong(), any());

        getMediaList("?folderId=" + FOLDER_ID)
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("FOLDER_NOT_FOUND"));
    }

    // 목록 항목의 필드가 단건 응답에도 그대로 펼쳐져야 한다. 한 겹 더 감싸이면 프론트가 갈라진다.
    @Test
    void 단건을_조회하면_200과_미디어를_반환한다() throws Exception {
        given(getMediaService.get(ROOM_ID, MEDIA_ID, MEMBER_ID)).willReturn(fullDetail());

        getMedia(MEDIA_ID)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.mediaId").value(MEDIA_ID))
            .andExpect(jsonPath("$.data.fileName").value("사진.jpg"))
            .andExpect(jsonPath("$.data.folderIds[0]").value(FOLDER_ID));
    }

    @Test
    void 단건에는_촬영_정보와_삭제_권한이_함께_나온다() throws Exception {
        given(getMediaService.get(ROOM_ID, MEDIA_ID, MEMBER_ID)).willReturn(fullDetail());

        getMedia(MEDIA_ID)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.takenAt").value("2026-08-01T12:30:00Z"))
            .andExpect(jsonPath("$.data.location.latitude").value(37.5665))
            .andExpect(jsonPath("$.data.location.longitude").value(126.978))
            .andExpect(jsonPath("$.data.location.name").doesNotExist())
            .andExpect(jsonPath("$.data.canDelete").value(true));
    }

    // EXIF 가 없는 사진이 훨씬 많다. 그때도 응답 구조가 흔들리면 안 된다.
    @Test
    void 촬영_정보가_없으면_location이_통째로_비어_있다() throws Exception {
        given(getMediaService.get(ROOM_ID, MEDIA_ID, MEMBER_ID))
            .willReturn(new MediaFullDetail(media(), null, null, false));

        getMedia(MEDIA_ID)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.takenAt").doesNotExist())
            .andExpect(jsonPath("$.data.location").doesNotExist())
            .andExpect(jsonPath("$.data.canDelete").value(false));
    }

    @Test
    void 없는_미디어를_조회하면_404() throws Exception {
        willThrow(new MediaNotFoundException())
            .given(getMediaService).get(anyLong(), anyLong(), anyLong());

        getMedia(MEDIA_ID)
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("MEDIA_NOT_FOUND"));
    }

    private MediaFullDetail fullDetail() {
        return new MediaFullDetail(media(), Instant.parse("2026-08-01T12:30:00Z"),
            new GeoPoint(new BigDecimal("37.566500"), new BigDecimal("126.978000")), true);
    }

    private MediaDetail media() {
        return new MediaDetail(MEDIA_ID, "IMAGE", "사진.jpg", "image/jpeg", 1024L,
            null, null, null, null, null, List.of(FOLDER_ID), 7L, "가현", "READY", Instant.now());
    }

    private ResultActions getMediaList(String query) throws Exception {
        return mockMvc.perform(get("/api/v1/rooms/" + ROOM_ID + "/media" + query)
            .header("Authorization", BEARER));
    }

    private ResultActions getMedia(Long mediaId) throws Exception {
        return mockMvc.perform(get("/api/v1/rooms/{roomId}/media/{mediaId}", ROOM_ID, mediaId)
            .header("Authorization", BEARER));
    }

    private Room activeRoom() {
        return Room.reconstruct(ROOM_ID, null, RoomCode.generate(new SecureRandom()),
            new RoomName("우테코 회식"), RoomStatus.initial(),
            new RoomExpiration(Instant.now().plusSeconds(3600)), UploadPolicy.ANYONE,
            1L, Instant.now(), null);
    }
}
