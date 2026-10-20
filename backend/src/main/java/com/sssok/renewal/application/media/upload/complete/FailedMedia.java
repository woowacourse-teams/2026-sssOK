package com.sssok.renewal.application.media.upload.complete;

import com.sssok.renewal.domain.media.upload.UploadRejectionReason;

public record FailedMedia(Long mediaId, String code, String message) {

    public static FailedMedia of(Long mediaId, UploadRejectionReason reason) {
        return new FailedMedia(mediaId, reason.name(), reason.message());
    }
}
