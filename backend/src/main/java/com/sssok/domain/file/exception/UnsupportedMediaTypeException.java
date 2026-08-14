package com.sssok.domain.file.exception;

public class UnsupportedMediaTypeException extends RuntimeException {

    public UnsupportedMediaTypeException(String value) {
        super("지원하지 않는 파일 형식입니다: " + value);
    }
}
