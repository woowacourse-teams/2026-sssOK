package com.sssok.domain.room.exception;

import com.sssok.common.exception.ErrorCode;
import com.sssok.common.exception.SssOkException;

public class InvalidUploadPolicyException extends SssOkException {

    public InvalidUploadPolicyException() {
        super(ErrorCode.INVALID_UPLOAD_POLICY);
    }
}
