package com.sssok.domain.admin.exception;

import com.sssok.common.exception.ErrorCode;
import com.sssok.common.exception.SssOkException;

public class InvalidAdminRoleException extends SssOkException {

    public InvalidAdminRoleException(String role) {
        super(ErrorCode.INVALID_ADMIN_ROLE, role);
    }
}
