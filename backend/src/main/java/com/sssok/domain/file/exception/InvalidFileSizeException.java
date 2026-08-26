package com.sssok.domain.file.exception;

import com.sssok.common.exception.ErrorCode;
import com.sssok.common.exception.SssOkException;

public class InvalidFileSizeException extends SssOkException {

    public InvalidFileSizeException(String message) {
        super(ErrorCode.INVALID_FILE_SIZE, message);
    }
}
