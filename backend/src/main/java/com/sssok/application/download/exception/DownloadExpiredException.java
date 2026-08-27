package com.sssok.application.download.exception;

import com.sssok.common.exception.ErrorCode;
import com.sssok.common.exception.SssOkException;

public class DownloadExpiredException extends SssOkException {

    public DownloadExpiredException() {
        super(ErrorCode.DOWNLOAD_EXPIRED);
    }
}
