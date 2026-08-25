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
@RestController
@RequestMapping("/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final CreateRoomService createRoomService;
    private final GetRoomService getRoomService;
    private final UpdateRoomService updateRoomService;
    private final DeleteRoomService deleteRoomService;
    private final JoinRoomService joinRoomService;

    @PostMapping
    public ResponseEntity<ApiResponse<RoomResponse>> createRoom(
        @AuthMember Long memberId,
        @RequestBody CreateRoomRequest request
    ) {
        RoomDetail detail = createRoomService.create(memberId, request.name());
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.of(RoomResponse.from(detail)));
    }

    @GetMapping("/{code}")
    public ApiResponse<RoomResponse> getRoom(
        @AuthMember(required = false) Long memberId,
        @PathVariable String code
    ) {
        return ApiResponse.of(RoomResponse.from(getRoomService.getByCode(new RoomCode(code), memberId)));
    }

    @PatchMapping("/{roomId}")
    public ApiResponse<RoomResponse> updateRoom(
        @AuthMember Long memberId,
        @PathVariable Long roomId,
        @RequestBody(required = false) UpdateRoomRequest request
    ) {
        UpdateRoomCommand command = UpdateRoomRequest.orEmpty(request).toCommand();
        return ApiResponse.of(RoomResponse.from(updateRoomService.update(roomId, memberId, command)));
    }

    @DeleteMapping("/{roomId}")
    public ApiResponse<DeleteRoomResponse> deleteRoom(@AuthMember Long memberId, @PathVariable Long roomId) {
        DeleteRoomResult result = deleteRoomService.delete(roomId, memberId);
        return ApiResponse.of(DeleteRoomResponse.from(result));
    }

    @PostMapping("/{roomId}/members")
    public ResponseEntity<ApiResponse<RoomMemberResponse>> joinRoom(
        @AuthMember Long memberId,
        @PathVariable Long roomId
    ) {
        JoinRoomResult result = joinRoomService.join(roomId, memberId);
        HttpStatus status = result.newlyJoined() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status)
            .body(ApiResponse.of(RoomMemberResponse.from(result)));
    }
}
