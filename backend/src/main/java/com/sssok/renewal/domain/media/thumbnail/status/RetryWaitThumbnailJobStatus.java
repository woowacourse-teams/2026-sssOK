package com.sssok.renewal.domain.media.thumbnail.status;

public final class RetryWaitThumbnailJobStatus implements ThumbnailJobStatus {
    public static final RetryWaitThumbnailJobStatus INSTANCE = new RetryWaitThumbnailJobStatus();

    private RetryWaitThumbnailJobStatus() {
    }

    @Override
    public String name() {
        return "RETRY_WAIT";
    }

    @Override
    public ThumbnailJobStatus claim() {
        return RunningThumbnailJobStatus.INSTANCE;
    }
}
