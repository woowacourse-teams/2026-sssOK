package com.sssok.domain.admin.exception;

import com.sssok.common.exception.ErrorCode;
import com.sssok.common.exception.SssOkException;

public class InvalidAdminNameException extends SssOkException {

    public InvalidAdminNameException() {
        super(ErrorCode.INVALID_ADMIN_NAME);
    }
}
