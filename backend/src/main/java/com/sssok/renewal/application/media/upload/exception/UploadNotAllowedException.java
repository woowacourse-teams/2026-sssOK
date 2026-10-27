package com.sssok.renewal.application.media.upload.exception;

import com.sssok.common.exception.ErrorCode;
import com.sssok.common.exception.SssOkException;

public class UploadNotAllowedException extends SssOkException {
    public UploadNotAllowedException() {
        super("방장만 업로드할 수 있는 방입니다", ErrorCode.UPLOAD_NOT_ALLOWED);
    }
}
