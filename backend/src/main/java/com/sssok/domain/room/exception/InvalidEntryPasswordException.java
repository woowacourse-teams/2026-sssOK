package com.sssok.domain.room.exception;

import com.sssok.common.exception.ErrorCode;
import com.sssok.common.exception.SssOkException;

public class InvalidEntryPasswordException extends SssOkException {

    public InvalidEntryPasswordException(String message) {
        super(ErrorCode.INVALID_ENTRY_PASSWORD, message);
    }
}
