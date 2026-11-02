package com.sssok.renewal.domain.download;

import com.sssok.domain.file.StorageKey;
import com.sssok.renewal.domain.download.status.DownloadJobStatus;
import java.time.Duration;
import java.time.Instant;
import lombok.Getter;

@Getter
public class DownloadJob {

    private final Long id;
    private final Long roomId;
    private final Long requesterId;
    private final int mediaCount;
    private final long totalSizeBytes;
    private final String fileName;
    private final Instant createdAt;

    private DownloadJobStatus status;
    private StorageKey zipStorageKey;
    private int progress;
    // 보관 기간은 READY 시점부터 센다. 압축이 길어져도 받을 수 있는 시간이 줄지 않게 한다.
    private Instant readyAt;
    private String failureReason;

    private DownloadJob(Long id, Long roomId, Long requesterId, DownloadJobStatus status,
                        int mediaCount, long totalSizeBytes, String fileName, StorageKey zipStorageKey,
                        int progress, Instant createdAt, Instant readyAt, String failureReason) {
        this.id = id;
        this.roomId = roomId;
        this.requesterId = requesterId;
        this.status = status;
        this.mediaCount = mediaCount;
        this.totalSizeBytes = totalSizeBytes;
        this.fileName = fileName;
        this.zipStorageKey = zipStorageKey;
        this.progress = progress;
        this.createdAt = createdAt;
        this.readyAt = readyAt;
        this.failureReason = failureReason;
    }

    public static DownloadJob create(Long roomId, Long requesterId, int mediaCount,
                                     long totalSizeBytes, String fileName, Instant now) {
        return new DownloadJob(null, roomId, requesterId, DownloadJobStatus.initial(),
            mediaCount, totalSizeBytes, fileName, null, 0, now, null, null);
    }

    public static DownloadJob reconstruct(Long id, Long roomId, Long requesterId,
                                          DownloadJobStatus status, int mediaCount, long totalSizeBytes,
                                          String fileName, StorageKey zipStorageKey, int progress,
                                          Instant createdAt, Instant readyAt, String failureReason) {
        return new DownloadJob(id, roomId, requesterId, status, mediaCount, totalSizeBytes,
            fileName, zipStorageKey, progress, createdAt, readyAt, failureReason);
    }

    public boolean isRequestedBy(Long memberId) {
        return requesterId.equals(memberId);
    }

    public boolean isExpired(Duration retention, Instant now) {
        return readyAt != null && readyAt.plus(retention).isBefore(now);
    }

    public void markRunning() {
        status = status.run();
    }

    public void markReady(StorageKey zipStorageKey, Instant now) {
        status = status.ready();
        this.zipStorageKey = zipStorageKey;
        this.readyAt = now;
        this.progress = 100;
    }

    public void markFailed(String reason) {
        status = status.fail();
        this.failureReason = reason;
    }

    public void markExpired() {
        status = status.expire();
    }

    public void updateProgress(int progress) {
        this.progress = progress;
    }
}
