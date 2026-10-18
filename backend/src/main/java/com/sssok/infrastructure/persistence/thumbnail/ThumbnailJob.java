package com.sssok.infrastructure.persistence.thumbnail;

import com.sssok.common.monitoring.MediaProcessingMetrics.Kind;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 미디어 공개 상태와 별개인 영속 작업 기록. 삭제된 미디어도 CANCELLED 이력을 남긴다.
@Entity
@Table(name = "thumbnail_job")
@Getter
@NoArgsConstructor
public class ThumbnailJob {
    public enum State { PENDING, RUNNING, RETRY_WAIT, SUCCEEDED, FAILED, CANCELLED }

    @Id
    @Column(name = "media_id")
    private Long mediaId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Kind kind;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private State state;
    @Column(nullable = false)
    private int attempts;
    @Column(name = "available_at", nullable = false)
    private Instant availableAt;
    @Column(name = "claim_token")
    private UUID claimToken;
    @Column(name = "lease_until")
    private Instant leaseUntil;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "completed_at")
    private Instant completedAt;
    @Column(name = "error_code", length = 100)
    private String errorCode;
    @Column(name = "thumbnail_key", length = 255)
    private String thumbnailKey;
    @Column(name = "preview_key", length = 255)
    private String previewKey;

    public ThumbnailJob(Long mediaId, Kind kind, Instant now) {
        this.mediaId = mediaId;
        this.kind = kind;
        state = State.PENDING;
        availableAt = createdAt = now;
    }

    public void claim(Instant now, Instant until) {
        state = State.RUNNING;
        claimToken = UUID.randomUUID();
        leaseUntil = until;
        attempts++;
    }

    public boolean owns(UUID token, Instant now) {
        return state == State.RUNNING && token.equals(claimToken) && leaseUntil.isAfter(now);
    }

    public void heartbeat(Instant until) { leaseUntil = until; }

    public void artifacts(String thumbnail, String preview) {
        thumbnailKey = thumbnail;
        previewKey = preview;
    }

    public void retry(Instant next, String code) {
        state = State.RETRY_WAIT;
        availableAt = next;
        errorCode = code;
        clearClaim();
    }

    public void rejected(Instant now) {
        attempts--;
        state = attempts == 0 ? State.PENDING : State.RETRY_WAIT;
        availableAt = now;
        clearClaim();
    }

    public void complete(State terminal, Instant now, String code) {
        state = terminal;
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
