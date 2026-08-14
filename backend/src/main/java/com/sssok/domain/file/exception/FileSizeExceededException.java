package com.sssok.domain.file.exception;

import com.sssok.domain.file.FileSize;
import com.sssok.domain.file.MediaType;

public class FileSizeExceededException extends RuntimeException {

    public FileSizeExceededException(MediaType mediaType, FileSize fileSize) {
        super("%s 파일은 최대 %d바이트까지 업로드할 수 있습니다. (요청: %d바이트)"
                .formatted(mediaType.name(), mediaType.maxBytes(), fileSize.bytes()));
    }
}
