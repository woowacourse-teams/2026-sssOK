package com.sssok.application.download.exception;

import com.sssok.common.exception.ErrorCode;
import com.sssok.common.exception.SssOkException;

public class DownloadNotFoundException extends SssOkException {

    public DownloadNotFoundException() {
        super(ErrorCode.DOWNLOAD_NOT_FOUND);
    }
}
