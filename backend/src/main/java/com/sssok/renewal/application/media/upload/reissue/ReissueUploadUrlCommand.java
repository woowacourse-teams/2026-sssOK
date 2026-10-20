package com.sssok.renewal.application.media.upload.reissue;

import com.sssok.renewal.application.media.upload.exception.InvalidUploadRequestException;

public record ReissueUploadUrlCommand(Long roomId, Long mediaId, Long requesterId, Long newSize) {
    public void validate() {
        if (newSize != null && newSize <= 0) {
            throw InvalidUploadRequestException.invalidFileSize();
        }
    }
}
