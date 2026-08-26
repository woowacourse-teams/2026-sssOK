package com.sssok.application.folder.exception;

public class DuplicateFolderNameException extends RuntimeException {

    public DuplicateFolderNameException() {
        super("이미 같은 이름의 폴더가 있습니다");
    }
}
