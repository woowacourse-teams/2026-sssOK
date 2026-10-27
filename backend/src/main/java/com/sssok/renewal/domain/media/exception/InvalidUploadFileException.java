package com.sssok.renewal.domain.media.exception;

public class InvalidUploadFileException extends UploadRejectedException {
    public InvalidUploadFileException() {
        super(Reason.INVALID_PARAM, "파일 정보가 올바르지 않습니다");
    }
}
