package com.sssok.application.admin.exception;

import com.sssok.common.exception.ErrorCode;
import com.sssok.common.exception.SssOkException;

// 슈퍼관리자가 0명이 되면 계정 관리를 할 수 있는 사람이 아무도 없어진다.
// DB 를 직접 고치는 것 말고는 복구 수단이 없어서 미리 막는다.
public class LastSuperAdminException extends SssOkException {

    public LastSuperAdminException() {
        super(ErrorCode.LAST_SUPER_ADMIN);
    }
}
