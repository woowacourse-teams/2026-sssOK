package com.sssok.presentation.api.common;

import com.sssok.application.admin.exception.AdminLoginRateLimitedException;
import com.sssok.application.feedback.exception.FeedbackRateLimitedException;
import com.sssok.common.exception.ErrorCode;
import com.sssok.common.exception.SssOkException;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.ObjectError;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String DEFAULT_VIOLATION_MESSAGE = "요청 값이 올바르지 않습니다";

    // 연속 등록 제한만 Retry-After 를 함께 내려준다.
    @ExceptionHandler(FeedbackRateLimitedException.class)
    public ResponseEntity<ErrorResponse> handleFeedbackRateLimited(FeedbackRateLimitedException e) {
        ErrorCode errorCode = e.errorCode();
        return ResponseEntity.status(HttpStatus.valueOf(errorCode.status()))
            .header(HttpHeaders.RETRY_AFTER, String.valueOf(e.getRetryAfterSeconds()))
            .body(new ErrorResponse(errorCode.name(), e.getMessage()));
    }

    @ExceptionHandler(AdminLoginRateLimitedException.class)
    public ResponseEntity<ErrorResponse> handleAdminLoginRateLimited(AdminLoginRateLimitedException e) {
        ErrorCode errorCode = e.errorCode();
        return ResponseEntity.status(HttpStatus.valueOf(errorCode.status()))
            .header(HttpHeaders.RETRY_AFTER, String.valueOf(e.getRetryAfterSeconds()))
            .body(new ErrorResponse(errorCode.name(), e.getMessage()));
    }

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

    // 메시지는 애너테이션에 적어둔 우리 문구라 그대로 내려도 안전하다.
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(MethodArgumentNotValidException e) {
        return respond(ErrorCode.INVALID_PARAM, firstViolationMessage(e));
    }

    // @RequestParam · @PathVariable 의 제약 위반. 등록하지 않으면 500 으로 새어 나간다.
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleMethodValidation(HandlerMethodValidationException e) {
        return respond(ErrorCode.INVALID_PARAM, DEFAULT_VIOLATION_MESSAGE);
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

    // 어느 핸들러에도 걸리지 않은 경로. 등록하지 않으면 오타 URL 하나가 500 으로 나간다.
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException e) {
        return respond(ErrorCode.API_NOT_FOUND);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException e) {
        return respond(ErrorCode.UNSUPPORTED_MEDIA_TYPE);
    }

    // Accept 가 JSON 을 받지 않는 요청. 등록하지 않으면 예상하지 못한 예외로 잡혀 ERROR 로그가 쌓인다.
    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<ErrorResponse> handleNotAcceptable(HttpMediaTypeNotAcceptableException e) {
        return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).build();
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        log.error("처리되지 않은 예외가 발생했습니다", e);
        return respond(ErrorCode.INTERNAL_SERVER_ERROR);
    }

    // 위반이 여러 개여도 첫 번째만 내려준다. 프론트는 한 번에 한 문구만 띄운다.
    private String firstViolationMessage(MethodArgumentNotValidException e) {
        return e.getBindingResult().getAllErrors().stream()
            .map(ObjectError::getDefaultMessage)
            .filter(Objects::nonNull)
            .findFirst()
            .orElse(DEFAULT_VIOLATION_MESSAGE);
    }

    private ResponseEntity<ErrorResponse> respond(ErrorCode errorCode, Object... args) {
        return ResponseEntity.status(HttpStatus.valueOf(errorCode.status()))
            .body(new ErrorResponse(errorCode.name(), errorCode.message(args)));
    }
}
