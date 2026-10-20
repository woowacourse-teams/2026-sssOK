package com.sssok.renewal.domain.media.exception;

public class UploadSizeExceededException extends UploadRejectedException {
    public UploadSizeExceededException() {
        super(Reason.FILE_TOO_LARGE, "파일 용량이 허용 크기를 초과했습니다");
    }
}
