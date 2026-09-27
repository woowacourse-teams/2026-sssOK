package com.sssok.domain.file.exception;

import com.sssok.common.exception.ErrorCode;
import com.sssok.common.exception.SssOkException;
import com.sssok.domain.file.FileSize;
import com.sssok.domain.file.MediaType;
import com.sssok.domain.file.UploadSizePolicy;

public class FileSizeExceededException extends SssOkException {

    public FileSizeExceededException(MediaType mediaType, FileSize fileSize,
                                     UploadSizePolicy sizePolicy) {
        super(ErrorCode.FILE_SIZE_EXCEEDED, mediaType.name(),
            sizePolicy.maxBytesFor(mediaType), fileSize.bytes());
    }
}
