package com.sssok.domain.file.exception;

import com.sssok.common.exception.ErrorCode;
import com.sssok.common.exception.SssOkException;

public class InvalidGeoPointException extends SssOkException {

    public InvalidGeoPointException(String message) {
        super(ErrorCode.INVALID_PARAM, message);
    }
}
