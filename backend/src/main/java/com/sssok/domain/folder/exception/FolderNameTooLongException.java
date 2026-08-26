package com.sssok.domain.folder.exception;

public class FolderNameTooLongException extends RuntimeException {

    public FolderNameTooLongException() {
        super("폴더 이름은 12자까지 입력할 수 있어요");
    }
}
