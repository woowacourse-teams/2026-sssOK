package com.sssok.application.media.exception;

import com.sssok.common.exception.ErrorCode;
import com.sssok.common.exception.SssOkException;

public class ThumbnailNotFoundException extends SssOkException {

    public ThumbnailNotFoundException() {
        super(ErrorCode.THUMBNAIL_NOT_FOUND);
    }
}
