package com.sssok.application.media.exception;

import com.sssok.common.exception.ErrorCode;
import com.sssok.common.exception.SssOkException;

public class MediaNotFoundException extends SssOkException {

    public MediaNotFoundException() {
        super(ErrorCode.MEDIA_NOT_FOUND);
    }
}
