package com.sssok.domain.auth.exception;

import com.sssok.common.exception.ErrorCode;
import com.sssok.common.exception.SssOkException;

public class InvalidLinkCodeException extends SssOkException {

    public InvalidLinkCodeException(String value) {
        super(ErrorCode.INVALID_LINK_CODE, value);
    }
}
