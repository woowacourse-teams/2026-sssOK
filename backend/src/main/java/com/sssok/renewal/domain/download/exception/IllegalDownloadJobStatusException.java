package com.sssok.renewal.domain.download.exception;

import com.sssok.common.exception.ErrorCode;
import com.sssok.common.exception.SssOkException;
import com.sssok.renewal.domain.download.status.DownloadJobStatus;

public class IllegalDownloadJobStatusException extends SssOkException {

    public IllegalDownloadJobStatusException(DownloadJobStatus current, DownloadJobStatus next) {
        super(ErrorCode.ILLEGAL_DOWNLOAD_JOB_STATUS, current.name(), next.name());
    }
}
