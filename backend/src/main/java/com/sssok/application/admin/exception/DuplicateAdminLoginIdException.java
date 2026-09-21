package com.sssok.application.admin.exception;

import com.sssok.common.exception.ErrorCode;
import com.sssok.common.exception.SssOkException;

public class DuplicateAdminLoginIdException extends SssOkException {

    public DuplicateAdminLoginIdException() {
        super(ErrorCode.DUPLICATE_ADMIN_LOGIN_ID);
    }
}
