package com.sssok.renewal.application.organization.execute;

import com.sssok.common.exception.ErrorCode;
import com.sssok.common.exception.SssOkException;

public class OrganizeExecutionException extends SssOkException {
    public enum Reason {
        NOT_DELIVERED,
        AUTHENTICATION_FAILED,
        TOOL_CHANGED,
        REJECTED,
        SERVER_BUSY,
        OUTCOME_UNKNOWN,
        INVALID_RESPONSE
    }

    private final Reason reason;

    public OrganizeExecutionException(Reason reason) {
        super("사진 정리 분석 실행 요청에 실패했습니다: " + reason, ErrorCode.INTERNAL_SERVER_ERROR);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
