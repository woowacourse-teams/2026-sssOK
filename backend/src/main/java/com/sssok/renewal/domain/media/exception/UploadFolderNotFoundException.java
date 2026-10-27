package com.sssok.renewal.domain.media.exception;

import com.sssok.common.exception.ErrorCode;
import com.sssok.common.exception.SssOkException;
import java.util.List;

public class UploadFolderNotFoundException extends SssOkException {
    public UploadFolderNotFoundException(List<Long> folderIds) {
        super("존재하지 않는 폴더입니다: " + folderIds, ErrorCode.FOLDER_NOT_FOUND);
    }
}
