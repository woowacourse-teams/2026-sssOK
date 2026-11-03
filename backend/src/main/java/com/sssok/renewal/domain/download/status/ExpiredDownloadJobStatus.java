package com.sssok.renewal.domain.download.status;

public final class ExpiredDownloadJobStatus implements DownloadJobStatus {
    public static final ExpiredDownloadJobStatus INSTANCE = new ExpiredDownloadJobStatus();

    private ExpiredDownloadJobStatus() {
    }

    @Override
    public String name() {
        return "EXPIRED";
    }
}
