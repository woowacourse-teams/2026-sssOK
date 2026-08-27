package com.sssok.application.mediafolder.exception;

import com.sssok.common.exception.ErrorCode;
import com.sssok.common.exception.SssOkException;

public class InvalidMediaFolderParamException extends SssOkException {

    public InvalidMediaFolderParamException() {
        super(ErrorCode.INVALID_PARAM, "미디어와 폴더를 선택해 주세요");
    }
}
