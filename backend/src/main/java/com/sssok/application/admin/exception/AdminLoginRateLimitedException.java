package com.sssok.application.admin.exception;

import com.sssok.common.exception.ErrorCode;
import com.sssok.common.exception.SssOkException;
import lombok.Getter;

// 로그인 실패가 반복되면 막는다.
@Getter
public class AdminLoginRateLimitedException extends SssOkException {

    private final long retryAfterSeconds;

    public AdminLoginRateLimitedException(long retryAfterSeconds) {
        super(ErrorCode.ADMIN_LOGIN_RATE_LIMITED);
        this.retryAfterSeconds = retryAfterSeconds;
    }
}
