package com.sssok.application.folder.exception;

import com.sssok.common.exception.ErrorCode;
import com.sssok.common.exception.SssOkException;

public class FolderNotFoundException extends SssOkException {

    public FolderNotFoundException(Long folderId) {
        super(ErrorCode.FOLDER_NOT_FOUND, folderId);
    }
}
