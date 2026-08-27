package com.sssok.application.media.exception;

import com.sssok.common.exception.ErrorCode;
import com.sssok.common.exception.SssOkException;

public class MediaForbiddenException extends SssOkException {

    public MediaForbiddenException() {
        super(ErrorCode.MEDIA_FORBIDDEN);
    }
}
