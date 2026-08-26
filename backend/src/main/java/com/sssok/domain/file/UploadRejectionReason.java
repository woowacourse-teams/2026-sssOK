package com.sssok.domain.file;

// 요청 전체를 실패시키지 않고 응답 본문에 담아 내려주는 파일별 사유.
// HTTP 에러가 아니라 200 응답 안의 항목이라 ErrorCode 와 별개로 둔다.
public enum UploadRejectionReason {

    FILE_TOO_LARGE("파일 용량이 허용 크기를 초과했습니다"),
    UNSUPPORTED_MEDIA_TYPE("이미지와 영상만 업로드할 수 있습니다"),
    INVALID_PARAM("파일 정보가 올바르지 않습니다");

    private final String message;

    UploadRejectionReason(String message) {
        this.message = message;
    }

    public String message() {
        return message;
    }
}
