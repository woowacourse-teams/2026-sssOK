package com.sssok.domain.room.exception;

public class PasscodeRequiredException extends RuntimeException {

    public PasscodeRequiredException() {
        super("입장 암호를 입력해주세요");
    }
}
