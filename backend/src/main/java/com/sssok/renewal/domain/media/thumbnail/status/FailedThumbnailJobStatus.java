package com.sssok.renewal.domain.media.thumbnail.status;

public final class FailedThumbnailJobStatus implements ThumbnailJobStatus {
    public static final FailedThumbnailJobStatus INSTANCE = new FailedThumbnailJobStatus();

    private FailedThumbnailJobStatus() {
    }

    @Override
    public String name() {
        return "FAILED";
    }

    @Override
    public boolean isFailed() {
        return true;
    }
}
