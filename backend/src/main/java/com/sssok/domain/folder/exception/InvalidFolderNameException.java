package com.sssok.domain.folder.exception;

public class InvalidFolderNameException extends RuntimeException {

    public InvalidFolderNameException() {
        super("폴더 이름을 입력해주세요");
    }
}
