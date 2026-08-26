package com.sssok.application.mediafolder.exception;

public class InvalidMediaFolderParamException extends RuntimeException {

    public InvalidMediaFolderParamException() {
        super("미디어와 폴더를 선택해 주세요");
    }
}
