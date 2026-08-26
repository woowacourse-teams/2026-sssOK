package com.sssok.presentation.api.common;

import com.sssok.application.auth.exception.LinkCodeExpiredException;
import com.sssok.application.auth.exception.LinkCodeNotFoundException;
import com.sssok.application.auth.exception.UnauthorizedException;
import com.sssok.application.folder.exception.DuplicateFolderNameException;
import com.sssok.application.folder.exception.FolderNotFoundException;
import com.sssok.application.mediafolder.exception.InvalidMediaFolderParamException;
import com.sssok.domain.auth.exception.InvalidLinkCodeException;
import com.sssok.domain.folder.exception.FolderNameTooLongException;
import com.sssok.domain.folder.exception.InvalidFolderNameException;
import com.sssok.domain.member.exception.InvalidNicknameException;
import com.sssok.domain.room.exception.InvalidRoomExpirationException;
import com.sssok.domain.room.exception.InvalidRoomNameException;
import com.sssok.domain.room.exception.InvalidUploadPolicyException;
import com.sssok.application.room.exception.EmptyPatchException;
import com.sssok.application.room.exception.NotRoomMemberException;
import com.sssok.application.room.exception.RoomAlreadyDeletedException;
import com.sssok.application.room.exception.RoomExpiredException;
import com.sssok.application.room.exception.RoomMembershipRequiredException;
import com.sssok.application.room.exception.RoomNotFoundException;
import com.sssok.domain.room.exception.InvalidRoomCodeException;
import com.sssok.domain.room.exception.RoomHostRequiredException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Slf4j
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

    @ExceptionHandler(InvalidLinkCodeException.class)
    public ResponseEntity<ErrorResponse> handleInvalidLinkCode(InvalidLinkCodeException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(new ErrorResponse("INVALID_LINK_CODE", "코드가 올바르지 않습니다"));
    }

    @ExceptionHandler(LinkCodeNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleLinkCodeNotFound(LinkCodeNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(new ErrorResponse("LINK_CODE_NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(LinkCodeExpiredException.class)
    public ResponseEntity<ErrorResponse> handleLinkCodeExpired(LinkCodeExpiredException e) {
        return ResponseEntity.status(HttpStatus.GONE)
            .body(new ErrorResponse("LINK_CODE_EXPIRED", e.getMessage()));
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
            .body(new ErrorResponse("NOT_ROOM_HOST", e.getMessage()));
    }

    @ExceptionHandler(InvalidRoomExpirationException.class)
    public ResponseEntity<ErrorResponse> handleInvalidRoomExpiration(InvalidRoomExpirationException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(new ErrorResponse("INVALID_ROOM_EXPIRATION", "만료 시간은 24시간 또는 72시간만 선택할 수 있습니다"));
    }

    @ExceptionHandler(InvalidUploadPolicyException.class)
    public ResponseEntity<ErrorResponse> handleInvalidUploadPolicy(InvalidUploadPolicyException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(new ErrorResponse("INVALID_UPLOAD_POLICY", "업로드 권한은 everyone 또는 host 만 선택할 수 있습니다"));
    }


    @ExceptionHandler(EmptyPatchException.class)
    public ResponseEntity<ErrorResponse> handleEmptyPatch(EmptyPatchException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(new ErrorResponse("EMPTY_PATCH", e.getMessage()));
    }

    @ExceptionHandler(RoomExpiredException.class)
    public ResponseEntity<ErrorResponse> handleRoomExpired(RoomExpiredException e) {
        return ResponseEntity.status(HttpStatus.GONE)
            .body(new ErrorResponse("ROOM_EXPIRED", e.getMessage()));
    }

    @ExceptionHandler(RoomAlreadyDeletedException.class)
    public ResponseEntity<ErrorResponse> handleRoomAlreadyDeleted(RoomAlreadyDeletedException e) {
        return ResponseEntity.status(HttpStatus.GONE)
            .body(new ErrorResponse("ROOM_ALREADY_DELETED", e.getMessage()));
    }

    @ExceptionHandler(RoomMembershipRequiredException.class)
    public ResponseEntity<ErrorResponse> handleRoomMembershipRequired(RoomMembershipRequiredException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(new ErrorResponse("ROOM_MEMBERSHIP_REQUIRED", e.getMessage()));
    }

    @ExceptionHandler(NotRoomMemberException.class)
    public ResponseEntity<ErrorResponse> handleNotRoomMember(NotRoomMemberException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(new ErrorResponse("NOT_ROOM_MEMBER", e.getMessage()));
    }

    @ExceptionHandler(InvalidFolderNameException.class)
    public ResponseEntity<ErrorResponse> handleInvalidFolderName(InvalidFolderNameException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(new ErrorResponse("INVALID_FOLDER_NAME", e.getMessage()));
    }

    @ExceptionHandler(FolderNameTooLongException.class)
    public ResponseEntity<ErrorResponse> handleFolderNameTooLong(FolderNameTooLongException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(new ErrorResponse("FOLDER_NAME_TOO_LONG", e.getMessage()));
    }

    @ExceptionHandler(DuplicateFolderNameException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateFolderName(DuplicateFolderNameException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(new ErrorResponse("DUPLICATE_FOLDER_NAME", e.getMessage()));
    }

    @ExceptionHandler(FolderNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleFolderNotFound(FolderNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(new ErrorResponse("FOLDER_NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(InvalidMediaFolderParamException.class)
    public ResponseEntity<ErrorResponse> handleInvalidMediaFolderParam(InvalidMediaFolderParamException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(new ErrorResponse("INVALID_PARAM", e.getMessage()));
    }



    // 본문이 깨졌거나 타입이 맞지 않는 요청
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(new ErrorResponse("INVALID_REQUEST_BODY", "요청 본문 형식이 올바르지 않습니다"));
    }

    // 조회는 code, 수정·삭제·입장은 roomId 라서 둘을 바꿔 부르면 여기로 온다.
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(new ErrorResponse("INVALID_REQUEST_PARAMETER",
                "%s 값의 형식이 올바르지 않습니다".formatted(e.getName())));
    }

    // 읽은 뒤 저장하기 전에 다른 요청이 같은 방을 바꾼 경우
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLock(OptimisticLockingFailureException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(new ErrorResponse("ROOM_MODIFIED", "방 정보가 방금 변경되었습니다. 다시 시도해주세요"));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
            .body(new ErrorResponse("METHOD_NOT_ALLOWED", "지원하지 않는 요청 방식입니다"));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException e) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
            .body(new ErrorResponse("UNSUPPORTED_MEDIA_TYPE", "지원하지 않는 요청 형식입니다"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        log.error("처리되지 않은 예외가 발생했습니다", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(new ErrorResponse("INTERNAL_SERVER_ERROR", "일시적인 오류가 발생했습니다. 잠시 후 다시 시도해주세요"));
    }
}
