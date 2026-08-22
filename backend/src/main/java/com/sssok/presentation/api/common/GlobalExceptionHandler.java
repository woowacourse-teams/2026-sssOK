package com.sssok.presentation.api.common;

import com.sssok.application.auth.exception.UnauthorizedException;
import com.sssok.domain.member.exception.InvalidNicknameException;
import com.sssok.domain.room.exception.InvalidRoomNameException;
import com.sssok.application.room.exception.RoomNotFoundException;
import com.sssok.domain.room.exception.InvalidRoomCodeException;
import com.sssok.domain.room.exception.RoomHostRequiredException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(InvalidNicknameException.class)
    public ResponseEntity<ErrorResponse> handleInvalidNickname(InvalidNicknameException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(new ErrorResponse("INVALID_NICKNAME", "닉네임을 입력해주세요"));
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorized(UnauthorizedException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(new ErrorResponse("UNAUTHORIZED", e.getMessage()));
    }

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

    @ExceptionHandler(InvalidRoomNameException.class)
    public ResponseEntity<ErrorResponse> handleInvalidRoomName(InvalidRoomNameException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(new ErrorResponse("INVALID_ROOM_NAME", e.getMessage()));
    }

    @ExceptionHandler(RoomHostRequiredException.class)
    public ResponseEntity<ErrorResponse> handleRoomHostRequired(RoomHostRequiredException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(new ErrorResponse("ROOM_HOST_REQUIRED", e.getMessage()));
    }
}
