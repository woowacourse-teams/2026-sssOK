package com.sssok.presentation.api.room;

import com.sssok.application.room.CreateRoomService;
import com.sssok.application.room.GetRoomService;
import com.sssok.domain.room.Room;
import com.sssok.domain.room.RoomCode;
import com.sssok.presentation.api.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final CreateRoomService createRoomService;
    private final GetRoomService getRoomService;

    @PostMapping
    public ResponseEntity<ApiResponse<RoomResponse>> createRoom(@RequestBody CreateRoomRequest request) {
        Room room = createRoomService.create(request.name());
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.of(RoomResponse.from(room)));
    }

    @GetMapping("/{code}")
    public ApiResponse<RoomResponse> getRoom(@PathVariable String code) {
        Room room = getRoomService.getByCode(new RoomCode(code));
        return ApiResponse.of(RoomResponse.from(room));
    }
}
