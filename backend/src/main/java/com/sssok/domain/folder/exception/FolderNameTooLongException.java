package com.sssok.domain.folder.exception;

import com.sssok.common.exception.ErrorCode;
import com.sssok.common.exception.SssOkException;

public class FolderNameTooLongException extends SssOkException {

    public FolderNameTooLongException() {
        super(ErrorCode.FOLDER_NAME_TOO_LONG);
    }
}
