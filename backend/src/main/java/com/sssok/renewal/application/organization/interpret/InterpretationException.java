package com.sssok.renewal.application.organization.interpret;

import com.sssok.common.exception.ErrorCode;
import com.sssok.common.exception.SssOkException;

public class InterpretationException extends SssOkException {
    public enum Reason {
        CALL_FAILED,
        CATALOG_UNAVAILABLE,
        INVALID_RESPONSE
    }

    private final Reason reason;

    public InterpretationException(Reason reason) {
        super("사진 정리 요청 해석에 실패했습니다: " + reason, ErrorCode.INTERNAL_SERVER_ERROR);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
