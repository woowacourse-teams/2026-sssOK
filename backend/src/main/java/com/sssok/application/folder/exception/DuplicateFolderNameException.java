package com.sssok.application.folder.exception;

import com.sssok.common.exception.ErrorCode;
import com.sssok.common.exception.SssOkException;

public class DuplicateFolderNameException extends SssOkException {

    public DuplicateFolderNameException() {
        super(ErrorCode.DUPLICATE_FOLDER_NAME);
    }
}
