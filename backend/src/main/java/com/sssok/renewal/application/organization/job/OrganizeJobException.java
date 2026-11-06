package com.sssok.renewal.application.organization.job;

import com.sssok.common.exception.ErrorCode;
import com.sssok.common.exception.SssOkException;

public class OrganizeJobException extends SssOkException {

    public OrganizeJobException(ErrorCode errorCode, Object... args) {
        super(errorCode, args);
    }
}
