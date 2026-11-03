package com.sssok.renewal.domain.download.status;

public final class FailedDownloadJobStatus implements DownloadJobStatus {
    public static final FailedDownloadJobStatus INSTANCE = new FailedDownloadJobStatus();

    private FailedDownloadJobStatus() {
    }

    @Override
    public String name() {
        return "FAILED";
    }
}
