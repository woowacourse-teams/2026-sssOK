package com.sssok.common.exception;

// 이름이 그대로 응답의 code 가 되므로, 이름을 바꾸면 API 계약이 바뀐다.
// 상태를 HttpStatus 가 아닌 int 로 두는 건 도메인 예외도 이 열거형을 쓰기 때문이다.
// 도메인은 Spring 에 의존하지 않아서, HttpStatus 로의 변환은 표현 계층이 맡는다.
public enum ErrorCode {

    // 400 Bad Request
    INVALID_NICKNAME(400, "닉네임은 1자 이상 12자 이하여야 합니다"),
    INVALID_LINK_CODE(400, "코드가 올바르지 않습니다"),
    INVALID_ROOM_CODE(400, "올바르지 않은 방 코드 형식입니다: %s"),
    INVALID_ROOM_NAME(400, "올바르지 않은 방 이름입니다: %s"),
    INVALID_ROOM_EXPIRATION(400, "만료 시간은 24시간 또는 72시간만 선택할 수 있습니다"),
    INVALID_UPLOAD_POLICY(400, "업로드 권한은 everyone 또는 host 만 선택할 수 있습니다"),
    INVALID_ENTRY_PASSWORD(400, "%s"),
    INVALID_FOLDER_NAME(400, "폴더 이름을 입력해주세요"),
    FOLDER_NAME_TOO_LONG(400, "폴더 이름은 12자까지 입력할 수 있어요"),
    INVALID_PARAM(400, "%s"),
    INVALID_FILE_SIZE(400, "%s"),
    INVALID_STORAGE_KEY(400, "%s"),
    ILLEGAL_ROOM_STATUS_TRANSITION(400, "허용되지 않는 상태 전이입니다: %s -> %s"),
    ILLEGAL_UPLOAD_STATUS(400, "업로드 상태를 %s 에서 %s 로 바꿀 수 없습니다"),
    ILLEGAL_DOWNLOAD_JOB_STATUS(400, "다운로드 잡 상태를 %s 에서 %s 로 바꿀 수 없습니다"),
    EMPTY_PATCH(400, "변경할 항목을 하나 이상 보내주세요"),
    INVALID_REQUEST_BODY(400, "요청 본문 형식이 올바르지 않습니다"),
    INVALID_REQUEST_PARAMETER(400, "%s 값의 형식이 올바르지 않습니다"),
    TOO_MANY_FILES(400, "한 번에 최대 %d개까지 다운로드할 수 있습니다"),

    // 401 Unauthorized
    UNAUTHORIZED(401, "%s"),

    // 403 Forbidden
    NOT_ROOM_HOST(403, "방장만 수행할 수 있는 작업입니다"),
    ROOM_MEMBERSHIP_REQUIRED(403, "입장한 방만 구독할 수 있습니다"),
    NOT_ROOM_MEMBER(403, "입장한 방에서만 이용할 수 있습니다"),
    UPLOAD_NOT_ALLOWED(403, "방장만 업로드할 수 있는 방입니다"),
    MEDIA_FORBIDDEN(403, "본인이 요청한 업로드가 아닙니다"),
    DOWNLOAD_FORBIDDEN(403, "본인이 요청한 다운로드가 아닙니다"),

    // 404 Not Found
    ROOM_NOT_FOUND(404, "존재하지 않는 방입니다: %s"),
    LINK_CODE_NOT_FOUND(404, "유효하지 않은 코드입니다"),
    FOLDER_NOT_FOUND(404, "존재하지 않는 폴더입니다: %s"),
    MEDIA_NOT_FOUND(404, "업로드 요청을 찾을 수 없습니다"),
    DOWNLOAD_NOT_FOUND(404, "다운로드 요청을 찾을 수 없습니다"),

    // 405 Method Not Allowed
    METHOD_NOT_ALLOWED(405, "지원하지 않는 요청 방식입니다"),

    // 409 Conflict
    ROOM_MODIFIED(409, "방 정보가 방금 변경되었습니다. 다시 시도해주세요"),
    DUPLICATE_FOLDER_NAME(409, "이미 같은 이름의 폴더가 있습니다"),
    UPLOAD_ALREADY_COMPLETED(409, "이미 업로드가 완료된 파일입니다"),
    MEDIA_NOT_READY(409, "아직 처리 중인 미디어입니다"),

    // 410 Gone
    ROOM_EXPIRED(410, "이미 사라진 방입니다"),
    ROOM_ALREADY_DELETED(410, "이미 삭제되었거나 만료된 방입니다"),
    LINK_CODE_EXPIRED(410, "만료된 코드입니다"),
    DOWNLOAD_EXPIRED(410, "다운로드 기한이 지났습니다"),

    // 413 Payload Too Large
    FILE_SIZE_EXCEEDED(413, "%s 파일은 최대 %d바이트까지 업로드할 수 있습니다. (요청: %d바이트)"),
    FILE_TOO_LARGE(413, "파일 용량이 허용 크기를 초과했습니다"),

    // 429 Too Many Requests
    UPLOAD_RETRY_EXCEEDED(429, "재시도 횟수를 초과했습니다. 처음부터 다시 올려주세요"),
    RATE_LIMITED(429, "진행 중인 다운로드 요청이 너무 많습니다. 잠시 후 다시 시도해주세요"),

    // 415 Unsupported Media Type
    UNSUPPORTED_MEDIA_TYPE(415, "지원하지 않는 요청 형식입니다"),
    UNSUPPORTED_FILE_TYPE(415, "지원하지 않는 파일 형식입니다: %s"),

    // 500 Internal Server Error
    INTERNAL_SERVER_ERROR(500, "일시적인 오류가 발생했습니다. 잠시 후 다시 시도해주세요");

    private final int status;
    private final String messageFormat;

    ErrorCode(int status, String messageFormat) {
        this.status = status;
        this.messageFormat = messageFormat;
    }

    public int status() {
        return status;
    }

    public String message(Object... args) {
        if (args.length == 0) {
            return messageFormat;
        }
        return messageFormat.formatted(args);
    }
}