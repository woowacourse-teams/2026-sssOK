package com.sssok.renewal.application.media.upload.exception;

import com.sssok.common.exception.ErrorCode;
import com.sssok.common.exception.SssOkException;

public class MediaForbiddenException extends SssOkException {
    public MediaForbiddenException() {
        super("본인이 요청한 업로드가 아닙니다", ErrorCode.MEDIA_FORBIDDEN);
    }
}
