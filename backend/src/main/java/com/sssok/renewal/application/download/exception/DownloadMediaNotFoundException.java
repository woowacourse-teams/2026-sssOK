package com.sssok.renewal.application.download.exception;

import com.sssok.common.exception.ErrorCode;
import com.sssok.common.exception.SssOkException;

// 없는 미디어와 실물이 없는 RESERVED·FAILED 미디어를 같은 404 로 응답한다.
public class DownloadMediaNotFoundException extends SssOkException {
    public DownloadMediaNotFoundException() {
        super(ErrorCode.MEDIA_NOT_FOUND);
    }
}
