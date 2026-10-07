package com.sssok.application.search.exception;

import com.sssok.common.exception.ErrorCode;
import com.sssok.common.exception.SssOkException;
import java.time.Instant;

public class AiCallException extends SssOkException {
    private final String safeMessage;
    private final String code;

    public enum RetryDisposition {
        // 제공자가 미처리를 확정했거나 동일 요청의 멱등 재실행을 보장할 때만 사용한다.
        SAFE_TO_RETRY,
        PERMANENT,
        // 타임아웃·연결 단절·일반 5xx만으로는 미처리를 확정할 수 없다.
        UNKNOWN_OUTCOME
    }

    private final RetryDisposition retryDisposition;
    private final Instant retryNotBefore;

    // 분류되지 않은 제공자 오류를 자동 재호출하지 않는다.
    protected AiCallException(String message, String code) {
        this(message, code, RetryDisposition.UNKNOWN_OUTCOME);
    }

    public AiCallException(String message, String code, RetryDisposition retryDisposition) {
        this(message, code, retryDisposition, null);
    }

    public AiCallException(String message, String code, RetryDisposition retryDisposition,
                           Instant retryNotBefore) {
        super(ErrorCode.INTERNAL_SERVER_ERROR);
        this.retryNotBefore = retryNotBefore;
        this.safeMessage = message;
        this.code = code;
        this.retryDisposition = java.util.Objects.requireNonNull(retryDisposition);
    }

    @Override
    public String getMessage() {
        return safeMessage;
    }

    public RetryDisposition retryDisposition() {
        return retryDisposition;
    }

    public Instant retryNotBefore() {
        return retryNotBefore;
    }

    public String code() {
        return code;
    }
}
