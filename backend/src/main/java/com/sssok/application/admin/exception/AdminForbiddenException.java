package com.sssok.application.admin.exception;

import com.sssok.common.exception.ErrorCode;
import com.sssok.common.exception.SssOkException;

// 권한이 모자라 거부한다. 어떤 권한이 필요했는지는 응답에 싣지 않는다 —
// 내부 권한 체계를 밖으로 알려줄 이유가 없다.
public class AdminForbiddenException extends SssOkException {

    public AdminForbiddenException() {
        super(ErrorCode.ADMIN_FORBIDDEN);
    }
}
