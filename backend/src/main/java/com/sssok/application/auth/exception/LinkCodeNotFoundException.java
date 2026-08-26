package com.sssok.application.auth.exception;

import com.sssok.common.exception.ErrorCode;
import com.sssok.common.exception.SssOkException;

public class LinkCodeNotFoundException extends SssOkException {

    public LinkCodeNotFoundException() {
        super(ErrorCode.LINK_CODE_NOT_FOUND);
    }
}
