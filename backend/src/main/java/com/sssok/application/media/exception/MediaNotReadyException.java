package com.sssok.application.media.exception;

import com.sssok.common.exception.ErrorCode;
import com.sssok.common.exception.SssOkException;

public class MediaNotReadyException extends SssOkException {

    public MediaNotReadyException() {
        super(ErrorCode.MEDIA_NOT_READY);
    }
}
