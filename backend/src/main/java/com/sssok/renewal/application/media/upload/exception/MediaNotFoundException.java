package com.sssok.renewal.application.media.upload.exception;

import com.sssok.common.exception.ErrorCode;
import com.sssok.common.exception.SssOkException;

public class MediaNotFoundException extends SssOkException {
    public MediaNotFoundException() {
        super("존재하지 않는 미디어입니다", ErrorCode.MEDIA_NOT_FOUND);
    }
}
