package com.sssok.application.download.exception;

import com.sssok.common.exception.ErrorCode;
import com.sssok.common.exception.SssOkException;

public class DownloadForbiddenException extends SssOkException {

    public DownloadForbiddenException() {
        super(ErrorCode.DOWNLOAD_FORBIDDEN);
    }
}
