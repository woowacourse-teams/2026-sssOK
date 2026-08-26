package com.sssok.domain.file.exception;

import com.sssok.common.exception.ErrorCode;
import com.sssok.common.exception.SssOkException;

public class UnsupportedMediaTypeException extends SssOkException {

    public UnsupportedMediaTypeException(String value) {
        super(ErrorCode.UNSUPPORTED_FILE_TYPE, value);
    }
}
