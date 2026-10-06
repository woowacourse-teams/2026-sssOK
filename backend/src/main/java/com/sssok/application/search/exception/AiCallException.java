package com.sssok.application.search.exception;

public class AiCallException extends RuntimeException {
    private final String code;

    protected AiCallException(String message, String code) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
