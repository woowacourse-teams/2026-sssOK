package com.sssok.presentation.api.common;

import com.sssok.common.exception.ErrorCode;
import com.sssok.common.exception.SssOkException;
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

    // 메시지는 예외를 만들 때 이미 완성돼 있어 ErrorCode 가 아닌 예외에서 꺼낸다.
    @ExceptionHandler(SssOkException.class)
    public ResponseEntity<ErrorResponse> handleSssOk(SssOkException e) {
        ErrorCode errorCode = e.errorCode();
        return ResponseEntity.status(HttpStatus.valueOf(errorCode.status()))
            .body(new ErrorResponse(errorCode.name(), e.getMessage()));
    }

    // 본문이 깨졌거나 타입이 맞지 않는 요청
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException e) {
        return respond(ErrorCode.INVALID_REQUEST_BODY);
    }

    // 조회는 code, 수정·삭제·입장은 roomId 라서 둘을 바꿔 부르면 여기로 온다.
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return respond(ErrorCode.INVALID_REQUEST_PARAMETER, e.getName());
    }

    // 읽은 뒤 저장하기 전에 다른 요청이 같은 방을 바꾼 경우
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLock(OptimisticLockingFailureException e) {
        return respond(ErrorCode.ROOM_MODIFIED);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        return respond(ErrorCode.METHOD_NOT_ALLOWED);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException e) {
        return respond(ErrorCode.UNSUPPORTED_MEDIA_TYPE);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        log.error("처리되지 않은 예외가 발생했습니다", e);
        return respond(ErrorCode.INTERNAL_SERVER_ERROR);
    }

    private ResponseEntity<ErrorResponse> respond(ErrorCode errorCode, Object... args) {
        return ResponseEntity.status(HttpStatus.valueOf(errorCode.status()))
            .body(new ErrorResponse(errorCode.name(), errorCode.message(args)));
    }
}