package com.sssok.domain.admin.exception;

import com.sssok.common.exception.ErrorCode;
import com.sssok.common.exception.SssOkException;

public class InvalidAdminPasswordException extends SssOkException {

    public InvalidAdminPasswordException() {
        super(ErrorCode.INVALID_ADMIN_PASSWORD);
    }
}
