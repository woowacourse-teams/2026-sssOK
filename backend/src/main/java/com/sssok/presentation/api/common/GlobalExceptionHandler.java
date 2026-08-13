package com.sssok.presentation.api.common;

import com.sssok.application.room.RoomNotFoundException;
import com.sssok.domain.room.InvalidRoomCodeException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(RoomNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleRoomNotFound(RoomNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(new ErrorResponse("ROOM_NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(InvalidRoomCodeException.class)
    public ResponseEntity<ErrorResponse> handleInvalidRoomCode(InvalidRoomCodeException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(new ErrorResponse("INVALID_ROOM_CODE", e.getMessage()));
    }
}
