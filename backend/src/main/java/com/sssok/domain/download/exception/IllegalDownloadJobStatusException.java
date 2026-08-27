package com.sssok.domain.download.exception;

import com.sssok.common.exception.ErrorCode;
import com.sssok.common.exception.SssOkException;
import com.sssok.domain.download.DownloadJobStatus;

public class IllegalDownloadJobStatusException extends SssOkException {

    public IllegalDownloadJobStatusException(DownloadJobStatus current, DownloadJobStatus next) {
        super(ErrorCode.ILLEGAL_DOWNLOAD_JOB_STATUS, current, next);
    }
}
