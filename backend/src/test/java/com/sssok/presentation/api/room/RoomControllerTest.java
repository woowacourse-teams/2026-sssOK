package com.sssok.presentation.api.room;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sssok.application.port.out.RoomMemberRepository;
import com.sssok.application.port.out.RoomRepository;
import com.sssok.application.port.out.TokenProvider;
import com.sssok.application.room.CreateRoomService;
import com.sssok.application.room.DeleteRoomResult;
import com.sssok.application.room.DeleteRoomService;
import com.sssok.application.room.GetRoomService;
import com.sssok.application.room.JoinRoomResult;
import com.sssok.application.room.JoinRoomService;
import com.sssok.application.room.RoomDetail;
import com.sssok.application.room.UpdateRoomCommand;
import com.sssok.application.room.UpdateRoomService;
import com.sssok.application.room.exception.EmptyPatchException;
import com.sssok.application.room.exception.RoomAlreadyDeletedException;
import com.sssok.application.room.exception.RoomExpiredException;
import com.sssok.application.room.exception.RoomNotFoundException;
import com.sssok.domain.room.Room;
import com.sssok.domain.room.RoomCode;
import com.sssok.domain.room.RoomExpiration;
import com.sssok.domain.room.RoomName;
import com.sssok.domain.room.UploadPolicy;
import com.sssok.domain.room.exception.InvalidRoomExpirationException;
import com.sssok.domain.room.exception.InvalidUploadPolicyException;
import com.sssok.domain.room.exception.RoomHostRequiredException;
import com.sssok.domain.room.roomstatus.RoomStatus;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

// Controller 슬라이스 테스트
@WebMvcTest(RoomController.class)
class RoomControllerTest {

    private static final String CODE = "A3F9K2M7";
    private static final Long ROOM_ID = 10L;
    private static final Long HOST_ID = 1L;
    private static final Long MEMBER_ID = 2L;
    private static final Instant EXPIRES_AT = Instant.parse("2026-08-14T00:00:00Z");
    private static final Instant CREATED_AT = Instant.parse("2026-08-13T00:00:00Z");
    private static final String BEARER = "Bearer valid-token";

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    CreateRoomService createRoomService;

    @MockitoBean
    GetRoomService getRoomService;

    @MockitoBean
    UpdateRoomService updateRoomService;

    @MockitoBean
    DeleteRoomService deleteRoomService;

    @MockitoBean
    JoinRoomService joinRoomService;

    // AuthMemberArgumentResolver가 의존하는 포트
    @MockitoBean
    TokenProvider tokenProvider;

    // WebConfig가 등록하는 RoomMembershipInterceptor가 요구하는 포트. 이 슬라이스의 경로는
    // 인터셉터 패턴과 무관하지만, 빈 자체는 컨텍스트에 함께 뜨므로 목으로 채워야 한다.
    @MockitoBean
    RoomRepository roomRepository;

    @MockitoBean
    RoomMemberRepository roomMemberRepository;

    @BeforeEach
    void setUp() {
        given(tokenProvider.parse("valid-token")).willReturn(MEMBER_ID);
    }

    private RoomDetail roomDetail(UploadPolicy uploadPolicy, boolean joined) {
        Room room = Room.reconstruct(
            ROOM_ID,
            null,
            new RoomCode(CODE),
            new RoomName("우테코 회식"),
            RoomStatus.initial(),
            new RoomExpiration(EXPIRES_AT),
            uploadPolicy,
            HOST_ID,
            CREATED_AT,
            null
        );
        return new RoomDetail(room, "가현", joined, 0, List.of());
    }

    @Test
    void 방을_생성하면_201과_roomId를_포함한_방_정보를_반환한다() throws Exception {
        given(createRoomService.create(eq(MEMBER_ID), anyString(), isNull(), isNull()))
            .willReturn(roomDetail(UploadPolicy.ANYONE, false));

        mockMvc.perform(post("/api/v1/rooms")
                .header("Authorization", BEARER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"우테코 회식\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.roomId").value(ROOM_ID))
            .andExpect(jsonPath("$.data.code").value(CODE))
            .andExpect(jsonPath("$.data.hostId").value(HOST_ID))
            .andExpect(jsonPath("$.data.hostName").value("가현"));
    }

    @Test
    void 방_생성_시_uploadPolicy와_expiryHours를_보내면_그대로_전달된다() throws Exception {
        given(createRoomService.create(eq(MEMBER_ID), anyString(), eq("host"), eq(72)))
            .willReturn(roomDetail(UploadPolicy.HOST_ONLY, true));

        mockMvc.perform(post("/api/v1/rooms")
                .header("Authorization", BEARER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"우테코 회식\",\"uploadPolicy\":\"host\",\"expiryHours\":72}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.uploadPolicy").value("host"))
            .andExpect(jsonPath("$.data.joined").value(true));
    }

    @Test
    void 방_생성에_인증이_없으면_401을_반환한다() throws Exception {
        mockMvc.perform(post("/api/v1/rooms")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"우테코 회식\"}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void 방을_조회하면_200과_방_정보를_반환한다() throws Exception {
        given(getRoomService.getByCode(any(), any())).willReturn(roomDetail(UploadPolicy.ANYONE, true));

        mockMvc.perform(get("/api/v1/rooms/{code}", CODE)
                .header("Authorization", BEARER))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.roomId").value(ROOM_ID))
            .andExpect(jsonPath("$.data.code").value(CODE))
            .andExpect(jsonPath("$.data.name").value("우테코 회식"))
            .andExpect(jsonPath("$.data.status").value("ACTIVE"))
            .andExpect(jsonPath("$.data.hostId").value(HOST_ID))
            .andExpect(jsonPath("$.data.hostName").value("가현"))
            .andExpect(jsonPath("$.data.uploadPolicy").value("everyone"))
            .andExpect(jsonPath("$.data.joined").value(true))
            .andExpect(jsonPath("$.data.expiresAt").value("2026-08-14T00:00:00Z"));
    }

    @Test
    void 방장_여부는_서버가_계산하지_않고_hostId만_내려준다() throws Exception {
        given(getRoomService.getByCode(any(), any())).willReturn(roomDetail(UploadPolicy.ANYONE, true));

        mockMvc.perform(get("/api/v1/rooms/{code}", CODE)
                .header("Authorization", BEARER))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.hostId").value(HOST_ID))
            .andExpect(jsonPath("$.data.isHost").doesNotExist());
    }

    @Test
    void 토큰_없이_조회하면_요청자를_null로_넘긴다() throws Exception {
        given(getRoomService.getByCode(any(), isNull())).willReturn(roomDetail(UploadPolicy.ANYONE, false));

        mockMvc.perform(get("/api/v1/rooms/{code}", CODE))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.joined").value(false));

        then(getRoomService).should().getByCode(any(), isNull());
    }

    @Test
    void 토큰을_보냈는데_잘못됐으면_조회도_401이다() throws Exception {
        given(tokenProvider.parse("bad-token"))
            .willThrow(new com.sssok.application.auth.exception.UnauthorizedException("다시 접속해주세요"));

        mockMvc.perform(get("/api/v1/rooms/{code}", CODE)
                .header("Authorization", "Bearer bad-token"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void 업로드_권한은_API_문자열로_직렬화된다() throws Exception {
        given(getRoomService.getByCode(any(), any())).willReturn(roomDetail(UploadPolicy.HOST_ONLY, true));

        mockMvc.perform(get("/api/v1/rooms/{code}", CODE))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.uploadPolicy").value("host"));
    }

    @Test
    void 방_설정을_수정하면_200과_조회와_같은_구성의_응답을_반환한다() throws Exception {
        given(updateRoomService.update(eq(ROOM_ID), eq(MEMBER_ID), any()))
            .willReturn(roomDetail(UploadPolicy.HOST_ONLY, true));

        mockMvc.perform(patch("/api/v1/rooms/{roomId}", ROOM_ID)
                .header("Authorization", BEARER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"2차 회식\",\"uploadPolicy\":\"host\",\"expiryHours\":72}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.roomId").value(ROOM_ID))
            .andExpect(jsonPath("$.data.code").value(CODE))
            .andExpect(jsonPath("$.data.hostId").value(HOST_ID))
            .andExpect(jsonPath("$.data.uploadPolicy").value("host"))
            .andExpect(jsonPath("$.data.joined").value(true));
    }

    @Test
    void 보내지_않은_항목은_null_커맨드로_전달된다() throws Exception {
        given(updateRoomService.update(eq(ROOM_ID), eq(MEMBER_ID),
            eq(new UpdateRoomCommand("2차 회식", null, null))))
            .willReturn(roomDetail(UploadPolicy.ANYONE, true));

        mockMvc.perform(patch("/api/v1/rooms/{roomId}", ROOM_ID)
                .header("Authorization", BEARER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"2차 회식\"}"))
            .andExpect(status().isOk());
    }

    @Test
    void 수정할_항목이_없으면_400과_EMPTY_PATCH를_반환한다() throws Exception {
        given(updateRoomService.update(anyLong(), anyLong(), any())).willThrow(new EmptyPatchException());

        mockMvc.perform(patch("/api/v1/rooms/{roomId}", ROOM_ID)
                .header("Authorization", BEARER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("EMPTY_PATCH"));
    }

    @Test
    void 본문을_아예_보내지_않아도_400과_EMPTY_PATCH를_반환한다() throws Exception {
        given(updateRoomService.update(anyLong(), anyLong(), any())).willThrow(new EmptyPatchException());

        mockMvc.perform(patch("/api/v1/rooms/{roomId}", ROOM_ID)
                .header("Authorization", BEARER))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("EMPTY_PATCH"));
    }

    @Test
    void 방장이_아니면_403과_NOT_ROOM_HOST를_반환한다() throws Exception {
        given(updateRoomService.update(anyLong(), anyLong(), any())).willThrow(new RoomHostRequiredException());

        mockMvc.perform(patch("/api/v1/rooms/{roomId}", ROOM_ID)
                .header("Authorization", BEARER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"2차 회식\"}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("NOT_ROOM_HOST"));
    }

    @Test
    void 저장_직전에_다른_요청이_방을_바꿨으면_409를_반환한다() throws Exception {
        given(updateRoomService.update(anyLong(), anyLong(), any()))
            .willThrow(new ObjectOptimisticLockingFailureException("room", ROOM_ID));

        mockMvc.perform(patch("/api/v1/rooms/{roomId}", ROOM_ID)
                .header("Authorization", BEARER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"2차 회식\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("ROOM_MODIFIED"));
    }

    @Test
    void 만료된_방을_수정하면_410과_ROOM_EXPIRED를_반환한다() throws Exception {
        given(updateRoomService.update(anyLong(), anyLong(), any())).willThrow(new RoomExpiredException());

        mockMvc.perform(patch("/api/v1/rooms/{roomId}", ROOM_ID)
                .header("Authorization", BEARER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"2차 회식\"}"))
            .andExpect(status().isGone())
            .andExpect(jsonPath("$.code").value("ROOM_EXPIRED"));
    }

    @Test
    void 허용되지_않은_만료_시간이면_400을_반환한다() throws Exception {
        given(updateRoomService.update(anyLong(), anyLong(), any()))
            .willThrow(new InvalidRoomExpirationException("48시간"));

        mockMvc.perform(patch("/api/v1/rooms/{roomId}", ROOM_ID)
                .header("Authorization", BEARER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"expiryHours\":48}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_ROOM_EXPIRATION"));
    }

    @Test
    void 알_수_없는_업로드_권한이면_400을_반환한다() throws Exception {
        given(updateRoomService.update(anyLong(), anyLong(), any()))
            .willThrow(new InvalidUploadPolicyException("nobody"));

        mockMvc.perform(patch("/api/v1/rooms/{roomId}", ROOM_ID)
                .header("Authorization", BEARER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"uploadPolicy\":\"nobody\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_UPLOAD_POLICY"));
    }

    @Test
    void 방을_삭제하면_200과_삭제_시각_영구삭제_예정_시각을_반환한다() throws Exception {
        given(deleteRoomService.delete(eq(ROOM_ID), eq(MEMBER_ID))).willReturn(new DeleteRoomResult(
            Instant.parse("2026-08-13T10:00:00Z"),
            Instant.parse("2026-08-20T10:00:00Z")));

        mockMvc.perform(delete("/api/v1/rooms/{roomId}", ROOM_ID)
                .header("Authorization", BEARER))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.deletedAt").value("2026-08-13T10:00:00Z"))
            .andExpect(jsonPath("$.data.purgeAt").value("2026-08-20T10:00:00Z"));
    }

    @Test
    void 이미_삭제된_방을_삭제하면_410과_ROOM_ALREADY_DELETED를_반환한다() throws Exception {
        given(deleteRoomService.delete(anyLong(), anyLong())).willThrow(new RoomAlreadyDeletedException());

        mockMvc.perform(delete("/api/v1/rooms/{roomId}", ROOM_ID)
                .header("Authorization", BEARER))
            .andExpect(status().isGone())
            .andExpect(jsonPath("$.code").value("ROOM_ALREADY_DELETED"));
    }

    @Test
    void 없는_방을_삭제하면_404를_반환한다() throws Exception {
        given(deleteRoomService.delete(anyLong(), anyLong())).willThrow(new RoomNotFoundException(ROOM_ID));

        mockMvc.perform(delete("/api/v1/rooms/{roomId}", ROOM_ID)
                .header("Authorization", BEARER))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("ROOM_NOT_FOUND"));
    }

    @Test
    void 처음_입장하면_201과_멤버_정보를_반환한다() throws Exception {
        given(joinRoomService.join(eq(ROOM_ID), eq(MEMBER_ID))).willReturn(
            new JoinRoomResult(ROOM_ID, MEMBER_ID, "민수", HOST_ID, CREATED_AT, true));

        performJoin("{}")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.roomId").value(ROOM_ID))
            .andExpect(jsonPath("$.data.userId").value(MEMBER_ID))
            .andExpect(jsonPath("$.data.displayName").value("민수"))
            .andExpect(jsonPath("$.data.hostId").value(HOST_ID))
            .andExpect(jsonPath("$.data.joinedAt").value("2026-08-13T00:00:00Z"))
            .andExpect(jsonPath("$.data.isHost").doesNotExist());
    }

    @Test
    void 표시_이름은_요청_바디로_받지_않는다() throws Exception {
        given(joinRoomService.join(eq(ROOM_ID), eq(MEMBER_ID))).willReturn(
            new JoinRoomResult(ROOM_ID, MEMBER_ID, "민수", HOST_ID, CREATED_AT, true));

        mockMvc.perform(post("/api/v1/rooms/{roomId}/members", ROOM_ID)
                .header("Authorization", BEARER))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.displayName").value("민수"));

        then(joinRoomService).should().join(eq(ROOM_ID), eq(MEMBER_ID));
    }

    @Test
    void 이미_참여_중인_사람이_다시_입장하면_200을_반환한다() throws Exception {
        given(joinRoomService.join(eq(ROOM_ID), eq(MEMBER_ID))).willReturn(
            new JoinRoomResult(ROOM_ID, MEMBER_ID, "민수", HOST_ID, CREATED_AT, false));

        performJoin("{}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.displayName").value("민수"));
    }



    @Test
    void 없는_방에_입장하면_404를_반환한다() throws Exception {
        given(joinRoomService.join(anyLong(), anyLong())).willThrow(new RoomNotFoundException(ROOM_ID));

        performJoin("{}")
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("ROOM_NOT_FOUND"));
    }

    @Test
    void 만료된_방에_입장하면_410을_반환한다() throws Exception {
        given(joinRoomService.join(anyLong(), anyLong())).willThrow(new RoomExpiredException());

        performJoin("{}")
            .andExpect(status().isGone())
            .andExpect(jsonPath("$.code").value("ROOM_EXPIRED"));
    }

    @Test
    void 입장에_인증이_없으면_401을_반환한다() throws Exception {
        mockMvc.perform(post("/api/v1/rooms/{roomId}/members", ROOM_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void 형식이_잘못된_방_코드로_조회하면_400을_반환한다() throws Exception {
        mockMvc.perform(get("/api/v1/rooms/{code}", "invalid-code"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_ROOM_CODE"));
    }

    private ResultActions performJoin(String body) throws Exception {
        return mockMvc.perform(post("/api/v1/rooms/{roomId}/members", ROOM_ID)
            .header("Authorization", BEARER)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
    }
}