package com.sssok.domain.folder.exception;

import com.sssok.common.exception.ErrorCode;
import com.sssok.common.exception.SssOkException;

public class InvalidFolderNameException extends SssOkException {

    public InvalidFolderNameException(String value) {
        super(ErrorCode.INVALID_FOLDER_NAME, value);
    }
}
