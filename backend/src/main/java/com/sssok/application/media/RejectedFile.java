package com.sssok.application.media;

import com.sssok.domain.file.UploadRejectionReason;

public record RejectedFile(String fileName, String code, String message) {

    public static RejectedFile of(String fileName, UploadRejectionReason reason) {
        return new RejectedFile(fileName, reason.name(), reason.message());
    }
}
