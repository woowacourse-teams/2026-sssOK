package com.sssok.domain.file.exception;

import com.sssok.common.exception.ErrorCode;
import com.sssok.common.exception.SssOkException;
import com.sssok.domain.file.FileSize;
import com.sssok.domain.file.MediaType;

public class FileSizeExceededException extends SssOkException {

    public FileSizeExceededException(MediaType mediaType, FileSize fileSize) {
        super(ErrorCode.FILE_SIZE_EXCEEDED, mediaType.name(), mediaType.maxBytes(), fileSize.bytes());
    }
}
