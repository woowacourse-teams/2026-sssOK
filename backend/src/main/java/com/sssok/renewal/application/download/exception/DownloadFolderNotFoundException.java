package com.sssok.renewal.application.download.exception;

import com.sssok.common.exception.ErrorCode;
import com.sssok.common.exception.SssOkException;

// 다른 방의 폴더도 없는 폴더와 같은 404 로 응답한다.
public class DownloadFolderNotFoundException extends SssOkException {
    public DownloadFolderNotFoundException(Long folderId) {
        super(ErrorCode.FOLDER_NOT_FOUND, folderId);
    }
}
