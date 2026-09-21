package com.sssok.domain.admin.exception;

import com.sssok.common.exception.ErrorCode;
import com.sssok.common.exception.SssOkException;

public class InvalidAdminLoginIdException extends SssOkException {

    public InvalidAdminLoginIdException() {
        super(ErrorCode.INVALID_ADMIN_LOGIN_ID);
    }
}
