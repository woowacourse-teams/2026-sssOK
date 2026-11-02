package com.sssok.renewal.domain.download.status;

public final class ReadyDownloadJobStatus implements DownloadJobStatus {
    public static final ReadyDownloadJobStatus INSTANCE = new ReadyDownloadJobStatus();

    private ReadyDownloadJobStatus() {
    }

    @Override
    public String name() {
        return "READY";
    }

    @Override
    public boolean isReady() {
        return true;
    }

    @Override
    public DownloadJobStatus expire() {
        return ExpiredDownloadJobStatus.INSTANCE;
    }
}
