package com.sssok.domain.room.exception;

public class InvalidUploadPolicyException extends RuntimeException {

    public InvalidUploadPolicyException(String value) {
        super("올바르지 않은 업로드 권한입니다: " + value);
    }
}
