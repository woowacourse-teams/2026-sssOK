package com.sssok.renewal.application.media.upload.exception;

import com.sssok.common.exception.ErrorCode;
import com.sssok.common.exception.SssOkException;

public class InvalidUploadRequestException extends SssOkException {
    private InvalidUploadRequestException(String message) {
        super(message, ErrorCode.INVALID_PARAM);
    }

    public static InvalidUploadRequestException invalidFileSize() {
        return new InvalidUploadRequestException("파일 크기가 올바르지 않습니다");
    }

    public static InvalidUploadRequestException emptyMediaIds() {
        return new InvalidUploadRequestException("등록할 미디어가 없습니다");
    }

    public static InvalidUploadRequestException emptyFiles() {
        return new InvalidUploadRequestException("업로드할 파일이 없습니다");
    }

    public static InvalidUploadRequestException missingFile() {
        return new InvalidUploadRequestException("파일 정보가 올바르지 않습니다");
    }
}
