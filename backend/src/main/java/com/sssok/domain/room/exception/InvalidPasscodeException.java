package com.sssok.domain.room.exception;

public class InvalidPasscodeException extends RuntimeException {

    public InvalidPasscodeException() {
        super("입장 암호가 올바르지 않습니다");
    }
}
