package com.sssok.application.search.exception;

import com.sssok.common.exception.ErrorCode;
import com.sssok.common.exception.SssOkException;

public class AiCallException extends SssOkException {
    private final String safeMessage;
    private final String code;

    protected AiCallException(String message, String code) {
        super(ErrorCode.INTERNAL_SERVER_ERROR);
        this.safeMessage = message;
        this.code = code;
    }

    @Override
    public String getMessage() {
        return safeMessage;
    }

    public String code() {
        return code;
    }
}
