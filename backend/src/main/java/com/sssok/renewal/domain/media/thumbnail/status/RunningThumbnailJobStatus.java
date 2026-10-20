package com.sssok.renewal.domain.media.thumbnail.status;

public final class RunningThumbnailJobStatus implements ThumbnailJobStatus {
    public static final RunningThumbnailJobStatus INSTANCE = new RunningThumbnailJobStatus();

    private RunningThumbnailJobStatus() {
    }

    @Override
    public String name() {
        return "RUNNING";
    }

    @Override
    public boolean isRunning() {
        return true;
    }

    @Override
    public ThumbnailJobStatus retry() {
        return RetryWaitThumbnailJobStatus.INSTANCE;
    }

    @Override
    public ThumbnailJobStatus rejected(int remainingAttempts) {
        return remainingAttempts == 0 ? PendingThumbnailJobStatus.INSTANCE : RetryWaitThumbnailJobStatus.INSTANCE;
    }

    @Override
    public ThumbnailJobStatus complete(ThumbnailJobStatus terminal) {
        if (terminal == SucceededThumbnailJobStatus.INSTANCE || terminal == FailedThumbnailJobStatus.INSTANCE
            || terminal == CancelledThumbnailJobStatus.INSTANCE) {
            return terminal;
        }
        return ThumbnailJobStatus.super.complete(terminal);
    }
}
