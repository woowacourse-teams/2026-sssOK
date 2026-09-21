package com.sssok.application.admin.exception;

import com.sssok.common.exception.ErrorCode;
import com.sssok.common.exception.SssOkException;

// 관리자이긴 하지만 계정 관리 권한이 없는 경우.
public class SuperAdminRequiredException extends SssOkException {

    public SuperAdminRequiredException() {
        super(ErrorCode.SUPER_ADMIN_REQUIRED);
    }
}
