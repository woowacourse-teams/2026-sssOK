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
    INVALID_CURSOR(400, "유효하지 않은 커서입니다"),
    INVALID_PAGE_SIZE(400, "페이지 크기는 1 이상 100 이하여야 합니다"),
    TOO_MANY_FILES(400, "한 번에 최대 %d개까지 처리할 수 있습니다"),
    INVALID_FEEDBACK_CONTENT(400, "의견을 입력해주세요"),
    FEEDBACK_TOO_LONG(400, "의견은 %d자까지 입력할 수 있습니다"),
    INVALID_ADMIN_LOGIN_ID(400, "아이디는 4~20자의 영소문자·숫자·밑줄만 쓸 수 있습니다"),
    INVALID_ADMIN_PASSWORD(400, "비밀번호는 8자 이상이어야 합니다"),
    INVALID_ADMIN_NAME(400, "이름은 1자 이상 20자 이하여야 합니다"),
    INVALID_ADMIN_ROLE(400, "올바르지 않은 역할입니다: %s"),
    INVALID_FEEDBACK_STATUS(400, "올바르지 않은 처리 상태입니다: %s"),
    INVALID_FEEDBACK_ASSIGNEE(400, "올바르지 않은 담당자입니다: %s"),
    INVALID_PUSH_SUBSCRIPTION(400, "%s"),
    INVALID_DEVICE_TOKEN(400, "%s"),
    INVALID_MEDIA_REPORT_REASON(400, "%s"),

    SOCIAL_AUTH_UNAVAILABLE(503, "소셜 인증 제공자에 연결할 수 없습니다. 잠시 후 다시 시도해주세요"),

    // 401 Unauthorized
    UNAUTHORIZED(401, "%s"),
    INVALID_SOCIAL_AUTH(401, "소셜 인증 정보가 유효하지 않습니다. 로그인을 다시 시작해주세요"),
    INVALID_CREDENTIALS(401, "아이디 또는 비밀번호가 올바르지 않습니다"),

    // 403 Forbidden
    INVALID_COOKIE_AUTH_REQUEST(403, "허용되지 않은 쿠키 인증 요청입니다"),
    NOT_ROOM_HOST(403, "방장만 수행할 수 있는 작업입니다"),
    ROOM_MEMBERSHIP_REQUIRED(403, "입장한 방만 구독할 수 있습니다"),
    NOT_ROOM_MEMBER(403, "입장한 방에서만 이용할 수 있습니다"),
    UPLOAD_NOT_ALLOWED(403, "방장만 업로드할 수 있는 방입니다"),
    MEDIA_FORBIDDEN(403, "본인이 요청한 업로드가 아닙니다"),
    DOWNLOAD_FORBIDDEN(403, "본인이 요청한 다운로드가 아닙니다"),
    ADMIN_FORBIDDEN(403, "권한이 없습니다"),
    SUPER_ADMIN_REQUIRED(403, "슈퍼관리자만 수행할 수 있는 작업입니다"),
    ORGANIZE_TRIAL_EXHAUSTED(403, "이 방에서 익명 회원의 체험 횟수를 모두 사용했습니다"),

    // 404 Not Found
    ROOM_NOT_FOUND(404, "존재하지 않는 방입니다: %s"),
    LINK_CODE_NOT_FOUND(404, "유효하지 않은 코드입니다"),
    FOLDER_NOT_FOUND(404, "존재하지 않는 폴더입니다: %s"),
    MEDIA_NOT_FOUND(404, "존재하지 않는 미디어입니다"),
    DOWNLOAD_NOT_FOUND(404, "다운로드 요청을 찾을 수 없습니다"),
    ADMIN_NOT_FOUND(404, "존재하지 않는 관리자입니다"),
    ORGANIZE_JOB_NOT_FOUND(404, "사진 정리 작업을 찾을 수 없습니다"),
    FEEDBACK_NOT_FOUND(404, "존재하지 않는 의견입니다"),
    API_NOT_FOUND(404, "존재하지 않는 API 경로입니다"),

    // 405 Method Not Allowed
    METHOD_NOT_ALLOWED(405, "지원하지 않는 요청 방식입니다"),

    // 409 Conflict
    ROOM_HOST_CANNOT_LEAVE(409, "방장은 방에서 나갈 수 없습니다"),
    SOCIAL_REAUTHENTICATION_REQUIRED(409, "소셜 계정 인증을 다시 진행해주세요"),
    SOCIAL_AUTH_CONFLICT(409, "회원 상태가 변경되었습니다. 로그인을 다시 시작해주세요"),
    ROOM_MODIFIED(409, "방 정보가 방금 변경되었습니다. 다시 시도해주세요"),
    DUPLICATE_FOLDER_NAME(409, "이미 같은 이름의 폴더가 있습니다"),
    UPLOAD_ALREADY_COMPLETED(409, "이미 업로드가 완료된 파일입니다"),
    DUPLICATE_ADMIN_LOGIN_ID(409, "이미 사용 중인 아이디입니다"),
    MEDIA_ALREADY_REPORTED(409, "이미 신고한 사진입니다"),
    LAST_SUPER_ADMIN(409, "마지막 슈퍼관리자는 삭제하거나 역할을 바꿀 수 없습니다"),
    ORGANIZE_JOB_IN_PROGRESS(409, "이 방에서 이미 사진 정리가 진행 중입니다"),
    ORGANIZE_JOB_NOT_READY(409, "아직 검토할 수 있는 결과가 없습니다"),
    ORGANIZE_JOB_NOT_CANCELABLE(409, "이미 반영된 사진 정리는 취소할 수 없습니다"),
    ORGANIZE_RETRY_NOT_ALLOWED(409, "다시 시도할 수 없는 작업입니다"),
    ORGANIZE_FOLDER_NAME_CONFLICT(409, "같은 이름의 폴더가 있습니다. 이름을 변경해주세요"),
    ORGANIZE_TARGET_CHANGED(409, "선택한 사진이 변경되었습니다. 결과를 다시 확인해주세요"),
    ORGANIZE_ALREADY_APPLIED(409, "이미 폴더에 반영된 작업입니다"),
    IDEMPOTENCY_KEY_CONFLICT(409, "같은 요청 키에 다른 요청 내용을 사용할 수 없습니다"),

    // 410 Gone
    ROOM_EXPIRED(410, "이미 사라진 방입니다"),
    ROOM_ALREADY_DELETED(410, "이미 삭제되었거나 만료된 방입니다"),
    LINK_CODE_EXPIRED(410, "만료된 코드입니다"),
    DOWNLOAD_EXPIRED(410, "다운로드 기한이 지났습니다"),
    ORGANIZE_REVIEW_EXPIRED(410, "사진 정리 결과의 검토 시간이 만료되었습니다"),
    ORGANIZE_RESULT_EXPIRED(410, "보관 기간이 지나 사진 정리 결과를 볼 수 없습니다"),

    // 422 Unprocessable Entity
    ORGANIZE_NEEDS_CLARIFICATION(422, "어떤 정리를 원하는지 더 구체적으로 알려주세요"),
    ORGANIZE_UNSUPPORTED_REQUEST(422, "지원하지 않는 사진 정리 요청입니다"),
    ORGANIZE_NO_ANALYZABLE_PHOTOS(422, "정리할 수 있는 사진이 없습니다"),
    ORGANIZE_TOO_MANY_PHOTOS(422, "정리할 사진이 너무 많습니다. 현재 %d장, 최대 %d장까지 정리할 수 있습니다"),

    // 413 Payload Too Large
    FILE_SIZE_EXCEEDED(413, "%s 파일은 최대 %d바이트까지 업로드할 수 있습니다. (요청: %d바이트)"),
    FILE_TOO_LARGE(413, "파일 용량이 허용 크기를 초과했습니다"),

    // 429 Too Many Requests
    UPLOAD_RETRY_EXCEEDED(429, "재시도 횟수를 초과했습니다. 처음부터 다시 올려주세요"),
    RATE_LIMITED(429, "진행 중인 다운로드 요청이 너무 많습니다. 잠시 후 다시 시도해주세요"),
    ADMIN_LOGIN_RATE_LIMITED(429, "로그인 시도가 너무 많습니다. 잠시 후 다시 시도해주세요"),
    UPLOAD_REQUEST_COOLDOWN(429, "방금 업로드를 요청했어요. 잠시 후 다시 요청해주세요"),
    ORGANIZE_RATE_LIMITED(429, "사진 정리 요청이 너무 많습니다. 잠시 후 다시 시도해주세요"),

    // 415 Unsupported Media Type
    UNSUPPORTED_MEDIA_TYPE(415, "지원하지 않는 요청 형식입니다"),
    UNSUPPORTED_FILE_TYPE(415, "지원하지 않는 파일 형식입니다: %s"),

    INVALID_SEARCH_QUERY(400, "검색어는 공백 정리 후 1~200자여야 합니다"),
    IMAGE_SEARCH_UNAVAILABLE(503, "이미지 검색을 사용할 수 없습니다. 잠시 후 다시 시도해주세요"),
    WEB_PUSH_UNAVAILABLE(503, "알림을 사용할 수 없습니다"),
    ORGANIZE_UNAVAILABLE(503, "지금은 사진 정리를 사용할 수 없습니다. 잠시 후 다시 시도해주세요"),

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
