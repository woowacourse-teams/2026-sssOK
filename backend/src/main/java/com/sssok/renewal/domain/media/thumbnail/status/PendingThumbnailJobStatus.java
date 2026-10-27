package com.sssok.renewal.domain.media.thumbnail.status;

public final class PendingThumbnailJobStatus implements ThumbnailJobStatus {
    public static final PendingThumbnailJobStatus INSTANCE = new PendingThumbnailJobStatus();

    private PendingThumbnailJobStatus() {
    }

    @Override
    public String name() {
        return "PENDING";
    }

    @Override
    public ThumbnailJobStatus claim() {
        return RunningThumbnailJobStatus.INSTANCE;
    }
}
