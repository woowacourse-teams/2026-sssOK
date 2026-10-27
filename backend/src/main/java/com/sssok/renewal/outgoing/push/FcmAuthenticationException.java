package com.sssok.renewal.outgoing.push;

import com.sssok.common.exception.ErrorCode;
import com.sssok.common.exception.SssOkException;

// 액세스 토큰을 받지 못했다. 기기 토큰과 상관없는 실패라 그 토큰을 만료로 보면 안 된다.
// FcmClient 안에서만 잡히고 응답으로 나가지 않는다.
public class FcmAuthenticationException extends SssOkException {
    public FcmAuthenticationException(String message) {
        super(message, ErrorCode.INTERNAL_SERVER_ERROR);
    }
}
