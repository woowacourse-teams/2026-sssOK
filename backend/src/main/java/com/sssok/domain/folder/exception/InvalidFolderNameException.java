package com.sssok.domain.folder.exception;

public class InvalidFolderNameException extends RuntimeException {

    public InvalidFolderNameException(String value) {
        super("올바르지 않은 폴더 이름입니다: " + value);
    }
}
