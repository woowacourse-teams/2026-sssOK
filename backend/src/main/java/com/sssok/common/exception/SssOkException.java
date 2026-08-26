package com.sssok.common.exception;

// 모든 예외가 이 타입을 거치므로 표현 계층이 핸들러 하나로 전부 받는다.
// 새 예외를 만들면서 핸들러 등록을 잊어 500 이 나가는 일이 구조적으로 생기지 않는다.
public abstract class SssOkException extends RuntimeException {

    private final transient ErrorCode errorCode;

    protected SssOkException(ErrorCode errorCode, Object... args) {
        super(errorCode.message(args));
        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}