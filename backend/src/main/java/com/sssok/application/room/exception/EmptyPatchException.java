package com.sssok.application.room.exception;

public class EmptyPatchException extends RuntimeException {

    public EmptyPatchException() {
        super("변경할 항목을 하나 이상 보내주세요");
    }
}
