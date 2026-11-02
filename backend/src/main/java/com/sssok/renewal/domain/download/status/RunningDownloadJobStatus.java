package com.sssok.renewal.domain.download.status;

public final class RunningDownloadJobStatus implements DownloadJobStatus {
    public static final RunningDownloadJobStatus INSTANCE = new RunningDownloadJobStatus();

    private RunningDownloadJobStatus() {
    }

    @Override
    public String name() {
        return "RUNNING";
    }

    @Override
    public DownloadJobStatus ready() {
        return ReadyDownloadJobStatus.INSTANCE;
    }

    @Override
    public DownloadJobStatus fail() {
        return FailedDownloadJobStatus.INSTANCE;
    }
}
