package com.sssok.presentation.api.room;

import com.sssok.application.room.CreateRoomService;
import com.sssok.application.room.DeleteRoomResult;
import com.sssok.application.room.DeleteRoomService;
import com.sssok.application.room.GetRoomService;
import com.sssok.application.room.JoinRoomResult;
import com.sssok.application.room.JoinRoomService;
import com.sssok.application.room.RoomDetail;
import com.sssok.application.room.UpdateRoomCommand;
import com.sssok.application.room.UpdateRoomService;
import com.sssok.domain.room.RoomCode;
import com.sssok.presentation.api.common.ApiResponse;
import com.sssok.presentation.auth.AuthMember;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 조회만 code 로 받고, 나머지는 roomId 로 받는다.
@Tag(name = "방", description = "링크 하나로 여는 단발성 공유방의 생성·조회·수정·삭제·입장")
@RestController
@RequestMapping("/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final CreateRoomService createRoomService;
    private final GetRoomService getRoomService;
    private final UpdateRoomService updateRoomService;
    private final DeleteRoomService deleteRoomService;
    private final JoinRoomService joinRoomService;

    @Operation(
        summary = "방 생성",
        description = "새 공유방을 만든다. 만든 사람이 방장(host)이 되고, 별도 입장 절차 없이 즉시 "
            + "방의 모든 권한(수정/삭제)을 가진다. 방 코드(code)는 이 API가 자동 생성해서 응답에 함께 내려준다. "
            + "사용법: 응답의 code로 공유 링크(예: https://sssok.app/rooms/{code})를 만들어 공유하면, "
            + "받은 사람은 GET /rooms/{code}로 방 정보를 확인할 수 있다."
    )
    @PostMapping
    public ResponseEntity<ApiResponse<RoomResponse>> createRoom(
        @Parameter(hidden = true) @AuthMember Long memberId,
        @RequestBody CreateRoomRequest request
    ) {
        RoomDetail detail = createRoomService.create(memberId, request.name());
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.of(RoomResponse.from(detail)));
    }

    @Operation(
        summary = "코드로 방 조회",
        description = "공유받은 코드로 방 정보를 조회한다. 다섯 개 API 중 유일하게 code(문자열)를 식별자로 받고, "
            + "나머지(수정/삭제/입장/구독)는 이 응답에 담긴 roomId(숫자)를 쓴다. "
            + "인증은 선택이다 — 토큰 없이도 조회는 되지만, joined(내가 이 방에 입장했는지)는 토큰이 있을 때만 정확히 계산된다. "
            + "사용법: 공유 링크에서 code를 꺼내 이 API를 먼저 호출하고, 응답의 roomId를 이후 모든 요청에 사용한다."
    )
    @GetMapping("/{code}")
    public ApiResponse<RoomResponse> getRoom(
        @Parameter(hidden = true) @AuthMember(required = false) Long memberId,
        @Parameter(description = "공유 링크에 담긴 방 코드") @PathVariable String code
    ) {
        return ApiResponse.of(RoomResponse.from(getRoomService.getByCode(new RoomCode(code), memberId)));
    }

    @Operation(
        summary = "방 설정 변경",
        description = "방 이름/업로드 권한/만료 시간 중 원하는 항목만 부분 수정한다(PATCH). 보내지 않은 필드는 "
            + "그대로 유지되고, 아무 필드도 안 보내면 400(EMPTY_PATCH)이 난다. 방장이 아니면 403이 난다. "
            + "사용법: 바꾸고 싶은 필드만 body에 담아 보낸다. 예를 들어 이름만 바꾸려면 "
            + "{ \"name\": \"새 이름\" } 만 보내면 된다."
    )
    @PatchMapping("/{roomId}")
    public ApiResponse<RoomResponse> updateRoom(
        @Parameter(hidden = true) @AuthMember Long memberId,
        @Parameter(description = "방 조회 응답의 roomId") @PathVariable Long roomId,
        @RequestBody(required = false) UpdateRoomRequest request
    ) {
        UpdateRoomCommand command = UpdateRoomRequest.orEmpty(request).toCommand();
        return ApiResponse.of(RoomResponse.from(updateRoomService.update(roomId, memberId, command)));
    }

    @Operation(
        summary = "방 삭제",
        description = "방을 soft delete한다. 삭제 즉시 새로운 입장/업로드는 막히지만, 일정 보존 기간(purgeAt) "
            + "동안은 데이터가 남아있다가 배치로 영구 삭제된다. 방장이 아니면 403, 이미 삭제된 방을 "
            + "다시 삭제하면 410이 난다."
    )
    @DeleteMapping("/{roomId}")
    public ApiResponse<DeleteRoomResponse> deleteRoom(
        @Parameter(hidden = true) @AuthMember Long memberId,
        @Parameter(description = "방 조회 응답의 roomId") @PathVariable Long roomId
    ) {
        DeleteRoomResult result = deleteRoomService.delete(roomId, memberId);
        return ApiResponse.of(DeleteRoomResponse.from(result));
    }

    @Operation(
        summary = "방 입장",
        description = "이 계정을 방의 참여자로 등록한다. 같은 사람이 여러 번 호출해도 참여 기록은 늘어나지 "
            + "않는다 — 처음 입장이면 201, 이미 입장한 상태에서 다시 호출하면 200을 반환한다(응답 바디는 동일). "
            + "사용법: 방 구독(SSE)이나 업로드 전에 이 API로 먼저 입장 처리를 해둬야 한다 "
            + "(미입장 상태로 구독을 시도하면 403이 난다)."
    )
    @PostMapping("/{roomId}/members")
    public ResponseEntity<ApiResponse<RoomMemberResponse>> joinRoom(
        @Parameter(hidden = true) @AuthMember Long memberId,
        @Parameter(description = "방 조회 응답의 roomId") @PathVariable Long roomId
    ) {
        JoinRoomResult result = joinRoomService.join(roomId, memberId);
        HttpStatus status = result.newlyJoined() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status)
            .body(ApiResponse.of(RoomMemberResponse.from(result)));
    }
}
