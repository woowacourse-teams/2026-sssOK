package com.sssok.application.media;

import com.sssok.domain.file.UploadRejectionReason;

public record FailedMedia(Long mediaId, String code, String message) {

    public static FailedMedia of(Long mediaId, UploadRejectionReason reason) {
        return new FailedMedia(mediaId, reason.name(), reason.message());
    }
}
