package com.sssok.infrastructure.ai;

import com.sssok.application.search.exception.AiCallException;

public class OpenAiCallException extends AiCallException {
    public OpenAiCallException(String code) {
        // 제공자 응답 본문·키·서명 URL을 예외와 로그에 노출하지 않는다.
        super("OpenAI 이미지 검색 호출 실패: " + code, code);
    }
}
