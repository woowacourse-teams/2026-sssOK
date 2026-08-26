package com.sssok.domain.file.exception;

import com.sssok.common.exception.ErrorCode;
import com.sssok.common.exception.SssOkException;

public class InvalidStorageKeyException extends SssOkException {

    public InvalidStorageKeyException(String message) {
        super(ErrorCode.INVALID_STORAGE_KEY, message);
    }
}
