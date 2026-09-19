package com.sssok.application.admin.exception;

import com.sssok.common.exception.ErrorCode;
import com.sssok.common.exception.SssOkException;

public class AdminNotFoundException extends SssOkException {

    public AdminNotFoundException() {
        super(ErrorCode.ADMIN_NOT_FOUND);
    }
}
