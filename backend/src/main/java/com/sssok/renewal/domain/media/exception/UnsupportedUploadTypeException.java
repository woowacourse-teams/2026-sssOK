package com.sssok.renewal.domain.media.exception;

public class UnsupportedUploadTypeException extends UploadRejectedException {
    public UnsupportedUploadTypeException() {
        super(Reason.UNSUPPORTED_MEDIA_TYPE, "이미지와 영상만 업로드할 수 있습니다");
    }
}
