package com.sssok.domain.file.exception;

import com.sssok.domain.file.UploadStatus;

public class IllegalUploadStatusException extends RuntimeException {

    public IllegalUploadStatusException(UploadStatus current, UploadStatus next) {
        super("업로드 상태를 %s 에서 %s 로 바꿀 수 없습니다.".formatted(current, next));
    }
}
