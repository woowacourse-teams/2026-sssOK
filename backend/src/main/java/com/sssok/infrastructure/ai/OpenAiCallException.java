package com.sssok.infrastructure.ai;

import com.sssok.application.search.exception.AiCallException;
import java.time.Instant;

public class OpenAiCallException extends AiCallException {
    public OpenAiCallException(String code) {
        this(code, RetryDisposition.PERMANENT, null);
    }

    public OpenAiCallException(String code, RetryDisposition disposition, Instant retryNotBefore) {
        // 제공자 응답 본문·키·서명 URL을 예외와 로그에 노출하지 않는다.
        super("OpenAI 이미지 검색 호출 실패: " + code, code, disposition, retryNotBefore);
    }
}
