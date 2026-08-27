package com.sssok.domain.download;

import java.util.Set;

// UploadStatus 와 같은 명시적 전이 방식. EXPIRED 는 사람이 직접 만들지 않고,
// 만료 배치가 보관 기간이 지난 READY 잡을 정리하며 붙인다.
public enum DownloadJobStatus {

    QUEUED,
    RUNNING,
    READY,
    FAILED,
    EXPIRED;

    private static final Set<DownloadJobStatus> FROM_QUEUED = Set.of(RUNNING);
    private static final Set<DownloadJobStatus> FROM_RUNNING = Set.of(READY, FAILED);
    private static final Set<DownloadJobStatus> FROM_READY = Set.of(EXPIRED);

    public boolean canTransitionTo(DownloadJobStatus next) {
        return switch (this) {
            case QUEUED -> FROM_QUEUED.contains(next);
            case RUNNING -> FROM_RUNNING.contains(next);
            case READY -> FROM_READY.contains(next);
            case FAILED, EXPIRED -> false;
        };
    }
}
