package com.sssok.renewal.domain.download.status;

public final class QueuedDownloadJobStatus implements DownloadJobStatus {
    public static final QueuedDownloadJobStatus INSTANCE = new QueuedDownloadJobStatus();

    private QueuedDownloadJobStatus() {
    }

    @Override
    public String name() {
        return "QUEUED";
    }

    @Override
    public DownloadJobStatus run() {
        return RunningDownloadJobStatus.INSTANCE;
    }
}
