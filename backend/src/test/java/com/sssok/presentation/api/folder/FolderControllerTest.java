package com.sssok.presentation.api.folder;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sssok.application.folder.CreateFolderService;
import com.sssok.application.folder.RenameFolderService;
import com.sssok.application.folder.exception.DuplicateFolderNameException;
import com.sssok.application.folder.exception.FolderNotFoundException;
import com.sssok.application.port.out.RoomMemberRepository;
import com.sssok.application.port.out.RoomRepository;
import com.sssok.application.port.out.TokenProvider;
import com.sssok.application.room.exception.NotRoomMemberException;
import com.sssok.application.room.exception.RoomExpiredException;
import com.sssok.application.room.exception.RoomNotFoundException;
import com.sssok.domain.folder.Folder;
import com.sssok.domain.folder.FolderName;
import com.sssok.domain.folder.exception.FolderNameTooLongException;
import com.sssok.domain.folder.exception.InvalidFolderNameException;
import com.sssok.domain.room.Room;
import com.sssok.domain.room.RoomCode;
import com.sssok.domain.room.RoomExpiration;
import com.sssok.domain.room.RoomMember;
import com.sssok.domain.room.RoomName;
import com.sssok.domain.room.UploadPolicy;
import com.sssok.domain.room.roomstatus.RoomStatus;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// Controller 슬라이스 테스트. RoomMembershipInterceptor도 이 슬라이스에 함께 뜨므로
// 방 존재/만료/입장 여부(404·410·403) 검증도 여기서 함께 다룬다.
@WebMvcTest(FolderController.class)
class FolderControllerTest {

    private static final Long ROOM_ID = 10L;
    private static final Long MEMBER_ID = 2L;
    private static final String BEARER = "Bearer valid-token";

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    CreateFolderService createFolderService;

    @MockitoBean
    RenameFolderService renameFolderService;

    @MockitoBean
    TokenProvider tokenProvider;

    @MockitoBean
    RoomRepository roomRepository;

    @MockitoBean
    RoomMemberRepository roomMemberRepository;

    @BeforeEach
    void setUp() {
        given(tokenProvider.parse("valid-token")).willReturn(MEMBER_ID);
        given(roomRepository.findById(ROOM_ID)).willReturn(java.util.Optional.of(activeRoom()));
        given(roomMemberRepository.findByRoomIdAndMemberId(ROOM_ID, MEMBER_ID))
            .willReturn(java.util.Optional.of(RoomMember.reconstruct(1L, ROOM_ID, MEMBER_ID, Instant.now())));
    }

    private Room activeRoom() {
        return Room.reconstruct(
            ROOM_ID,
            null,
            RoomCode.generate(new java.security.SecureRandom()),
            new RoomName("우테코 회식"),
            RoomStatus.initial(),
            new RoomExpiration(Instant.now().plusSeconds(3600)),
            UploadPolicy.ANYONE,
            1L,
            Instant.now(),
            null
        );
    }

    private Folder folder(Long id, String name) {
        return Folder.reconstruct(id, ROOM_ID, new FolderName(name), Instant.now());
    }

    @Test
    void 폴더를_생성하면_201과_폴더_정보를_반환한다() throws Exception {
        given(createFolderService.create(ROOM_ID, "맛집")).willReturn(folder(100L, "맛집"));

        createFolder("{\"name\":\"맛집\"}")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.id").value(100))
            .andExpect(jsonPath("$.data.name").value("맛집"))
            .andExpect(jsonPath("$.data.photoCount").value(0));
    }

    @Test
    void 인증이_없으면_401을_반환한다() throws Exception {
        mockMvc.perform(post("/api/v1/rooms/{roomId}/folders", ROOM_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"맛집\"}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void 없는_방이면_404와_ROOM_NOT_FOUND를_반환한다() throws Exception {
        given(roomRepository.findById(ROOM_ID)).willReturn(java.util.Optional.empty());

        createFolder("{\"name\":\"맛집\"}")
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("ROOM_NOT_FOUND"));
    }

    @Test
    void 만료된_방이면_410과_ROOM_EXPIRED를_반환한다() throws Exception {
        Room expired = Room.reconstruct(
            ROOM_ID, null, RoomCode.generate(new java.security.SecureRandom()), new RoomName("만료방"),
            RoomStatus.initial(), new RoomExpiration(Instant.now().minusSeconds(3600)),
            UploadPolicy.ANYONE, 1L, Instant.now(), null
        );
        given(roomRepository.findById(ROOM_ID)).willReturn(java.util.Optional.of(expired));

        createFolder("{\"name\":\"맛집\"}")
            .andExpect(status().isGone())
            .andExpect(jsonPath("$.code").value("ROOM_EXPIRED"));
    }

    @Test
    void 입장하지_않았으면_403과_NOT_ROOM_MEMBER를_반환한다() throws Exception {
        given(roomMemberRepository.findByRoomIdAndMemberId(ROOM_ID, MEMBER_ID))
            .willReturn(java.util.Optional.empty());

        createFolder("{\"name\":\"맛집\"}")
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("NOT_ROOM_MEMBER"));
    }

    @Test
    void 이름이_비어있으면_400과_INVALID_FOLDER_NAME을_반환한다() throws Exception {
        given(createFolderService.create(anyLong(), anyString())).willThrow(new InvalidFolderNameException());

        createFolder("{\"name\":\"\"}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_FOLDER_NAME"));
    }

    @Test
    void 이름이_12자를_넘으면_400과_FOLDER_NAME_TOO_LONG을_반환한다() throws Exception {
        given(createFolderService.create(anyLong(), anyString())).willThrow(new FolderNameTooLongException());

        createFolder("{\"name\":\"" + "가".repeat(13) + "\"}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("FOLDER_NAME_TOO_LONG"));
    }

    @Test
    void 이름이_중복되면_409와_DUPLICATE_FOLDER_NAME을_반환한다() throws Exception {
        given(createFolderService.create(anyLong(), anyString())).willThrow(new DuplicateFolderNameException());

        createFolder("{\"name\":\"맛집\"}")
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("DUPLICATE_FOLDER_NAME"));
    }

    @Test
    void 이름을_바꾸면_200과_바뀐_폴더_정보를_반환한다() throws Exception {
        given(renameFolderService.rename(ROOM_ID, 100L, "카페")).willReturn(folder(100L, "카페"));

        renameFolder(100L, "{\"name\":\"카페\"}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value(100))
            .andExpect(jsonPath("$.data.name").value("카페"))
            .andExpect(jsonPath("$.data.photoCount").value(0));
    }

    @Test
    void 없는_폴더면_404와_FOLDER_NOT_FOUND를_반환한다() throws Exception {
        given(renameFolderService.rename(anyLong(), anyLong(), anyString()))
            .willThrow(new FolderNotFoundException(100L));

        renameFolder(100L, "{\"name\":\"카페\"}")
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("FOLDER_NOT_FOUND"));
    }

    @Test
    void 이름_변경도_중복되면_409와_DUPLICATE_FOLDER_NAME을_반환한다() throws Exception {
        given(renameFolderService.rename(anyLong(), anyLong(), anyString()))
            .willThrow(new DuplicateFolderNameException());

        renameFolder(100L, "{\"name\":\"카페\"}")
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("DUPLICATE_FOLDER_NAME"));
    }

    private org.springframework.test.web.servlet.ResultActions createFolder(String body) throws Exception {
        return mockMvc.perform(post("/api/v1/rooms/{roomId}/folders", ROOM_ID)
            .header("Authorization", BEARER)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
    }

    private org.springframework.test.web.servlet.ResultActions renameFolder(Long folderId, String body) throws Exception {
        return mockMvc.perform(patch("/api/v1/rooms/{roomId}/folders/{folderId}", ROOM_ID, folderId)
            .header("Authorization", BEARER)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
    }
}
