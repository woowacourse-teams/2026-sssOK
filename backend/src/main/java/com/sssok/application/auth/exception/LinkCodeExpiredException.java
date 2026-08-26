package com.sssok.application.auth.exception;

import com.sssok.common.exception.ErrorCode;
import com.sssok.common.exception.SssOkException;

public class LinkCodeExpiredException extends SssOkException {

    public LinkCodeExpiredException() {
        super(ErrorCode.LINK_CODE_EXPIRED);
    }
}
