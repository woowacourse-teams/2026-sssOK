package com.sssok.renewal.domain.media.thumbnail;

import com.sssok.renewal.domain.media.thumbnail.status.ThumbnailJobStatus;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

@Getter
public class ThumbnailJob {
    private final Long mediaId;
    private final ThumbnailKind kind;
    private ThumbnailJobStatus status;
    private int attempts;
    private Instant availableAt;
    private UUID claimToken;
    private Instant leaseUntil;
    private final Instant createdAt;
    private Instant completedAt;
    private String errorCode;
    private String thumbnailKey;
    private String previewKey;

    public ThumbnailJob(Long mediaId, ThumbnailKind kind, Instant now) {
        this(mediaId, kind, ThumbnailJobStatus.initial(), 0, now, null, null, now, null, null, null, null);
    }

    private ThumbnailJob(Long mediaId, ThumbnailKind kind, ThumbnailJobStatus status, int attempts, Instant availableAt,
                         UUID claimToken, Instant leaseUntil, Instant createdAt, Instant completedAt,
                         String errorCode, String thumbnailKey, String previewKey) {
        this.mediaId = mediaId;
        this.kind = kind;
        this.status = status;
        this.attempts = attempts;
        this.availableAt = availableAt;
        this.claimToken = claimToken;
        this.leaseUntil = leaseUntil;
        this.createdAt = createdAt;
        this.completedAt = completedAt;
        this.errorCode = errorCode;
        this.thumbnailKey = thumbnailKey;
        this.previewKey = previewKey;
    }

    public static ThumbnailJob reconstruct(Long mediaId, ThumbnailKind kind, ThumbnailJobStatus status, int attempts,
                                           Instant availableAt, UUID claimToken, Instant leaseUntil,
                                           Instant createdAt, Instant completedAt, String errorCode,
                                           String thumbnailKey, String previewKey) {
        return new ThumbnailJob(mediaId, kind, status, attempts, availableAt, claimToken, leaseUntil,
            createdAt, completedAt, errorCode, thumbnailKey, previewKey);
    }

    public void claim(Instant now, Instant until) {
        status = status.claim();
        claimToken = UUID.randomUUID();
        leaseUntil = until;
        attempts++;
    }

    public boolean owns(UUID token, Instant now) {
        return status.isRunning() && token.equals(claimToken) && leaseUntil.isAfter(now);
    }

    public void heartbeat(Instant until) { leaseUntil = until; }

    public void artifacts(String thumbnail, String preview) {
        thumbnailKey = thumbnail;
        previewKey = preview;
    }

    public void retry(Instant next, String code) {
        status = status.retry();
        availableAt = next;
        errorCode = code;
        clearClaim();
    }

    public void rejected(Instant now) {
        status = status.rejected(attempts - 1);
        attempts--;
        availableAt = now;
        clearClaim();
    }

    public void complete(ThumbnailJobStatus terminal, Instant now, String code) {
        status = status.complete(terminal);
        completedAt = now;
        errorCode = code;
        clearClaim();
    }

    private void clearClaim() {
        claimToken = null;
        leaseUntil = null;
        thumbnailKey = null;
        previewKey = null;
    }
}
