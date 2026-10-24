package com.sssok.renewal.domain.member.exception;

import com.sssok.common.exception.ErrorCode;
import com.sssok.common.exception.SssOkException;

public class InvalidNicknameException extends SssOkException {

    public InvalidNicknameException() {
        super(ErrorCode.INVALID_NICKNAME);
    }
}
