package com.sssok.application.admin.exception;

import com.sssok.common.exception.ErrorCode;
import com.sssok.common.exception.SssOkException;

// 아이디나 비밀번호 칸 자체가 비어 온 경우. 값이 틀린 것(401)과 달리 요청이 형식을 못 갖춘 것이라 400이다.
public class InvalidLoginRequestException extends SssOkException {

    public InvalidLoginRequestException() {
        super(ErrorCode.INVALID_REQUEST_BODY);
    }
}
