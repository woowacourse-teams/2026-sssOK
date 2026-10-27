package com.sssok.renewal.incoming.auth;

import com.sssok.common.exception.ErrorCode;
import com.sssok.common.exception.SssOkException;

public class AuthTokenRequestException extends SssOkException {
    public AuthTokenRequestException(ErrorCode errorCode) {
        super(errorCode);
    }
}
