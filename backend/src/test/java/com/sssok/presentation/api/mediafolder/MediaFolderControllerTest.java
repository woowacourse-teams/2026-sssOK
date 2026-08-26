package com.sssok.presentation.api.mediafolder;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
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
import com.sssok.application.port.out.RoomMemberRepository;
import com.sssok.application.port.out.RoomRepository;
import com.sssok.application.port.out.TokenProvider;
import com.sssok.application.room.exception.NotRoomMemberException;
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

// Controller 슬라이스 테스트. RoomMembershipInterceptor도 이 슬라이스에 함께 뜨므로
// 방 존재/만료/입장 여부(404·410·403) 검증도 여기서 함께 다룬다.
@WebMvcTest(MediaFolderController.class)
class MediaFolderControllerTest {

    private static final Long ROOM_ID = 10L;
    private static final Long MEMBER_ID = 2L;
    private static final String BEARER = "Bearer valid-token";

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    AddMediaToFoldersService addMediaToFoldersService;

    @MockitoBean
    RemoveMediaFromFoldersService removeMediaFromFoldersService;

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
        return Room.reconstruct(
            ROOM_ID,
            null,
            RoomCode.generate(new SecureRandom()),
            new RoomName("우테코 회식"),
            RoomStatus.initial(),
            new RoomExpiration(Instant.now().plusSeconds(3600)),
            UploadPolicy.ANYONE,
            1L,
            Instant.now(),
            null
        );
    }

    @Test
    void 폴더에_담으면_200과_결과를_반환한다() throws Exception {
        AddMediaToFoldersResult result = new AddMediaToFoldersResult(
            4, 0, List.of(), List.of(new FolderSummary(31L, "맛집", 9), new FolderSummary(32L, "단체사진", 13)));
        given(addMediaToFoldersService.add(anyLong(), anyList(), anyList())).willReturn(result);

        addToFolders("{\"mediaIds\":[5012,5011],\"folderIds\":[31,32]}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.updatedCount").value(4))
            .andExpect(jsonPath("$.data.alreadyInCount").value(0))
            .andExpect(jsonPath("$.data.notFoundMediaIds").isEmpty())
            .andExpect(jsonPath("$.data.folders[0].id").value(31))
            .andExpect(jsonPath("$.data.folders[0].name").value("맛집"))
            .andExpect(jsonPath("$.data.folders[0].photoCount").value(9));
    }

    @Test
    void 인증이_없으면_401을_반환한다() throws Exception {
        mockMvc.perform(put("/api/v1/rooms/{roomId}/media/folders", ROOM_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"mediaIds\":[1],\"folderIds\":[31]}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void 없는_방이면_404와_ROOM_NOT_FOUND를_반환한다() throws Exception {
        given(roomRepository.findById(ROOM_ID)).willReturn(Optional.empty());

        addToFolders("{\"mediaIds\":[1],\"folderIds\":[31]}")
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("ROOM_NOT_FOUND"));
    }

    @Test
    void 입장하지_않았으면_403과_NOT_ROOM_MEMBER를_반환한다() throws Exception {
        given(roomMemberRepository.findByRoomIdAndMemberId(ROOM_ID, MEMBER_ID)).willReturn(Optional.empty());

        addToFolders("{\"mediaIds\":[1],\"folderIds\":[31]}")
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("NOT_ROOM_MEMBER"));
    }

    @Test
    void 없는_폴더가_있으면_404와_FOLDER_NOT_FOUND를_반환한다() throws Exception {
        given(addMediaToFoldersService.add(anyLong(), anyList(), anyList()))
            .willThrow(new FolderNotFoundException(List.of(999L)));

        addToFolders("{\"mediaIds\":[1],\"folderIds\":[999]}")
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("FOLDER_NOT_FOUND"));
    }

    @Test
    void mediaIds나_folderIds가_비어있으면_400과_INVALID_PARAM을_반환한다() throws Exception {
        given(addMediaToFoldersService.add(anyLong(), anyList(), anyList()))
            .willThrow(new InvalidMediaFolderParamException());

        addToFolders("{\"mediaIds\":[],\"folderIds\":[31]}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_PARAM"));
    }

    @Test
    void 폴더에서_꺼내면_200과_결과를_반환한다() throws Exception {
        RemoveMediaFromFoldersResult result = new RemoveMediaFromFoldersResult(
            2, List.of(5011L), List.of(), List.of(new FolderSummary(31L, "맛집", 7)));
        given(removeMediaFromFoldersService.remove(anyLong(), anyList(), anyList())).willReturn(result);

        removeFromFolders("{\"mediaIds\":[5012,5011],\"folderIds\":[31]}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.updatedCount").value(2))
            .andExpect(jsonPath("$.data.movedToRootMediaIds[0]").value(5011))
            .andExpect(jsonPath("$.data.folders[0].id").value(31));
    }

    @Test
    void 꺼내기에서_폴더_없이_보내면_null로_전달된다() throws Exception {
        RemoveMediaFromFoldersResult result =
            new RemoveMediaFromFoldersResult(1, List.of(5011L), List.of(), List.of());
        given(removeMediaFromFoldersService.remove(anyLong(), anyList(), org.mockito.ArgumentMatchers.isNull()))
            .willReturn(result);

        removeFromFolders("{\"mediaIds\":[5011]}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.movedToRootMediaIds[0]").value(5011));
    }

    @Test
    void 꺼내기에서_mediaIds가_비어있으면_400과_INVALID_PARAM을_반환한다() throws Exception {
        given(removeMediaFromFoldersService.remove(anyLong(), anyList(), anyList()))
            .willThrow(new InvalidMediaFolderParamException());

        removeFromFolders("{\"mediaIds\":[],\"folderIds\":[31]}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_PARAM"));
    }

    @Test
    void 꺼내기도_없는_폴더가_있으면_404와_FOLDER_NOT_FOUND를_반환한다() throws Exception {
        given(removeMediaFromFoldersService.remove(anyLong(), anyList(), anyList()))
            .willThrow(new FolderNotFoundException(List.of(999L)));

        removeFromFolders("{\"mediaIds\":[1],\"folderIds\":[999]}")
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("FOLDER_NOT_FOUND"));
    }

    private ResultActions addToFolders(String body) throws Exception {
        return mockMvc.perform(put("/api/v1/rooms/{roomId}/media/folders", ROOM_ID)
            .header("Authorization", BEARER)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
    }

    private ResultActions removeFromFolders(String body) throws Exception {
        return mockMvc.perform(delete("/api/v1/rooms/{roomId}/media/folders", ROOM_ID)
            .header("Authorization", BEARER)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
    }
}
