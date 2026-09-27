package com.sssok.presentation.api.mediafolder;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sssok.application.folder.exception.FolderNotFoundException;
import com.sssok.application.mediafolder.AddMediaToFoldersResult;
import com.sssok.application.mediafolder.AddMediaToFoldersService;
import com.sssok.application.mediafolder.FolderSummary;
import com.sssok.application.mediafolder.RemoveMediaFromFoldersResult;
import com.sssok.application.mediafolder.RemoveMediaFromFoldersService;
import com.sssok.application.mediafolder.exception.InvalidMediaFolderParamException;
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

@WebMvcTest(MediaFolderController.class)
class MediaFolderControllerTest {

    private static final Long ROOM_ID = 10L;
    private static final Long MEMBER_ID = 2L;
    private static final String BEARER = "Bearer valid-token";

    @Autowired MockMvc mockMvc;
    @MockitoBean AddMediaToFoldersService addMediaToFoldersService;
    @MockitoBean RemoveMediaFromFoldersService removeMediaFromFoldersService;
    @MockitoBean TokenProvider tokenProvider;
    @MockitoBean AdminTokenProvider adminTokenProvider;
    @MockitoBean RoomRepository roomRepository;
    @MockitoBean RoomMemberRepository roomMemberRepository;

    @BeforeEach
    void setUp() {
        given(tokenProvider.parse("valid-token")).willReturn(MEMBER_ID);
        given(roomRepository.findById(ROOM_ID)).willReturn(Optional.of(activeRoom()));
        given(roomMemberRepository.findByRoomIdAndMemberId(ROOM_ID, MEMBER_ID))
            .willReturn(Optional.of(RoomMember.reconstruct(1L, ROOM_ID, MEMBER_ID, Instant.now())));
    }

    @Test
    void 폴더에_담으면_결과를_반환한다() throws Exception {
        given(addMediaToFoldersService.add(anyLong(), anyList(), anyLong())).willReturn(
            new AddMediaToFoldersResult(2, 0, List.of(), new FolderSummary(31L, "맛집", 9)));

        addToFolders("{\"mediaIds\":[5012,5011],\"folderId\":31}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.updatedCount").value(2))
            .andExpect(jsonPath("$.data.folder.photoCount").value(9));
        verify(addMediaToFoldersService).add(ROOM_ID, List.of(5012L, 5011L), 31L);
    }

    @Test
    void 인증이_없으면_401을_반환한다() throws Exception {
        mockMvc.perform(put("/api/v1/rooms/{roomId}/media/folders", ROOM_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"mediaIds\":[1],\"folderId\":31}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void 없는_방이면_404를_반환한다() throws Exception {
        given(roomRepository.findById(ROOM_ID)).willReturn(Optional.empty());
        addToFolders("{\"mediaIds\":[1],\"folderId\":31}")
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("ROOM_NOT_FOUND"));
    }

    @Test
    void 입장하지_않았으면_403을_반환한다() throws Exception {
        given(roomMemberRepository.findByRoomIdAndMemberId(ROOM_ID, MEMBER_ID))
            .willReturn(Optional.empty());
        addToFolders("{\"mediaIds\":[1],\"folderId\":31}")
            .andExpect(status().isForbidden());
    }

    @Test
    void 없는_폴더면_404를_반환한다() throws Exception {
        given(addMediaToFoldersService.add(anyLong(), anyList(), anyLong()))
            .willThrow(new FolderNotFoundException(List.of(999L)));
        addToFolders("{\"mediaIds\":[1],\"folderId\":999}")
            .andExpect(status().isNotFound());
    }

    @Test
    void folderId가_없으면_400을_반환한다() throws Exception {
        given(addMediaToFoldersService.add(anyLong(), anyList(), any()))
            .willThrow(new InvalidMediaFolderParamException());
        addToFolders("{\"mediaIds\":[1]}")
            .andExpect(status().isBadRequest());
    }

    @Test
    void 폴더에서_꺼내면_결과를_반환한다() throws Exception {
        given(removeMediaFromFoldersService.remove(anyLong(), anyList(), anyList())).willReturn(
            new RemoveMediaFromFoldersResult(
                2, List.of(5011L), List.of(), List.of(new FolderSummary(31L, "맛집", 7))));

        removeFromFolders("{\"mediaIds\":[5012,5011],\"folderIds\":[31]}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.updatedCount").value(2))
            .andExpect(jsonPath("$.data.movedToRootMediaIds[0]").value(5011));
        verify(removeMediaFromFoldersService)
            .remove(ROOM_ID, List.of(5012L, 5011L), List.of(31L));
    }

    @Test
    void folderIds를_생략하면_null로_전달한다() throws Exception {
        given(removeMediaFromFoldersService.remove(anyLong(), anyList(), any())).willReturn(
            new RemoveMediaFromFoldersResult(1, List.of(5011L), List.of(), List.of()));
        removeFromFolders("{\"mediaIds\":[5011]}").andExpect(status().isOk());
        verify(removeMediaFromFoldersService).remove(ROOM_ID, List.of(5011L), null);
    }

    @Test
    void 꺼내기에서_mediaIds가_없으면_400을_반환한다() throws Exception {
        given(removeMediaFromFoldersService.remove(anyLong(), any(), anyList()))
            .willThrow(new InvalidMediaFolderParamException());
        removeFromFolders("{\"folderIds\":[31]}").andExpect(status().isBadRequest());
    }

    @Test
    void 꺼내기도_없는_폴더가_있으면_404를_반환한다() throws Exception {
        given(removeMediaFromFoldersService.remove(anyLong(), anyList(), anyList()))
            .willThrow(new FolderNotFoundException(List.of(999L)));
        removeFromFolders("{\"mediaIds\":[1],\"folderIds\":[999]}")
            .andExpect(status().isNotFound());
    }

    private ResultActions addToFolders(String body) throws Exception {
        return mockMvc.perform(put("/api/v1/rooms/{roomId}/media/folders", ROOM_ID)
            .header("Authorization", BEARER).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions removeFromFolders(String body) throws Exception {
        return mockMvc.perform(delete("/api/v1/rooms/{roomId}/media/folders", ROOM_ID)
            .header("Authorization", BEARER).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private Room activeRoom() {
        return Room.reconstruct(ROOM_ID, null, RoomCode.generate(new SecureRandom()),
            new RoomName("우테코 회식"), RoomStatus.initial(),
            new RoomExpiration(Instant.now().plusSeconds(3600)), UploadPolicy.ANYONE,
            1L, Instant.now(), null);
    }
}
