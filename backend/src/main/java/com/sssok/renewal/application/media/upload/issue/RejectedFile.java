package com.sssok.renewal.application.media.upload.issue;

import com.sssok.renewal.domain.media.exception.UploadRejectedException;

public record RejectedFile(String fileName, String code, String message) {
    public static RejectedFile from(UploadCandidate file, UploadRejectedException exception) {
        return new RejectedFile(file.fileName(), exception.reason().name(), exception.getMessage());
    }
}
