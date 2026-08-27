package com.sssok.domain.download;

import com.sssok.domain.download.exception.IllegalDownloadJobStatusException;
import com.sssok.domain.file.StorageKey;
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
    // READY 가 된 시각. 보관 기간은 여기서부터 센다 — 압축이 오래 걸리는 큰 방일수록
    // 링크가 생기기도 전에 유효기간이 줄어드는 것을 막기 위함이다.
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
        return new DownloadJob(null, roomId, requesterId, DownloadJobStatus.QUEUED,
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

    // READY 가 아니면(아직 진행 중이거나 실패) 만료 여부를 따질 대상이 아니다.
    public boolean isExpired(Duration retention, Instant now) {
        return readyAt != null && readyAt.plus(retention).isBefore(now);
    }

    public void markRunning() {
        transitionTo(DownloadJobStatus.RUNNING);
    }

    public void markReady(StorageKey zipStorageKey, Instant now) {
        transitionTo(DownloadJobStatus.READY);
        this.zipStorageKey = zipStorageKey;
        this.readyAt = now;
        this.progress = 100;
    }

    public void markFailed(String reason) {
        transitionTo(DownloadJobStatus.FAILED);
        this.failureReason = reason;
    }

    public void markExpired() {
        transitionTo(DownloadJobStatus.EXPIRED);
    }

    public void updateProgress(int progress) {
        this.progress = progress;
    }

    private void transitionTo(DownloadJobStatus next) {
        if (!status.canTransitionTo(next)) {
            throw new IllegalDownloadJobStatusException(status, next);
        }
        status = next;
    }
}
