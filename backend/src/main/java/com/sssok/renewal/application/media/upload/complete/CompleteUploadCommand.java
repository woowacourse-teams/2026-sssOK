package com.sssok.renewal.application.media.upload.complete;

import com.sssok.renewal.application.media.upload.exception.InvalidUploadRequestException;
import java.util.List;

public record CompleteUploadCommand(Long roomId, Long uploaderId, List<Long> mediaIds) {
    public void validate() {
        if (mediaIds == null || mediaIds.isEmpty()) {
            throw InvalidUploadRequestException.emptyMediaIds();
        }
    }
}
