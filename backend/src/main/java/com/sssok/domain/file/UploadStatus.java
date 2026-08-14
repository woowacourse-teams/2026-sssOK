package com.sssok.domain.file;

import java.util.Set;

public enum UploadStatus {

    PENDING,
    UPLOADING,
    COMPLETED,
    FAILED;

    private static final Set<UploadStatus> FROM_PENDING = Set.of(UPLOADING, FAILED);
    private static final Set<UploadStatus> FROM_UPLOADING = Set.of(COMPLETED, FAILED);
    private static final Set<UploadStatus> FROM_FAILED = Set.of(UPLOADING);

    public boolean canTransitionTo(UploadStatus next) {
        return switch (this) {
            case PENDING -> FROM_PENDING.contains(next);
            case UPLOADING -> FROM_UPLOADING.contains(next);
            case FAILED -> FROM_FAILED.contains(next);
            case COMPLETED -> false;
        };
    }

    public boolean isRetryable() {
        return this == FAILED;
    }
}
