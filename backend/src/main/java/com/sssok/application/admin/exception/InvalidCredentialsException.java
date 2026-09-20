package com.sssok.application.admin.exception;

import com.sssok.common.exception.ErrorCode;
import com.sssok.common.exception.SssOkException;

// 아이디가 없든 비밀번호가 틀리든 이 예외 하나만 던진다.
// 둘을 구분해 응답하면 어떤 아이디가 존재하는지 알려주는 꼴이 된다.
public class InvalidCredentialsException extends SssOkException {

    public InvalidCredentialsException() {
        super(ErrorCode.INVALID_CREDENTIALS);
    }
}
