package com.sssok.renewal.outgoing.database;

import com.sssok.renewal.domain.media.thumbnail.ThumbnailJob;
import com.sssok.renewal.domain.media.thumbnail.ThumbnailKind;
import com.sssok.renewal.domain.media.thumbnail.status.ThumbnailJobStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// DB 스키마는 Flyway가 관리한다. 도메인 상태를 컬럼으로 저장하는 JPA 모델이다.
@Entity
@Table(name = "thumbnail_job")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ThumbnailJobJpaEntity {
    @Id
    @Column(name = "media_id")
    private Long mediaId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ThumbnailKind kind;
    @Column(nullable = false, length = 20)
    private String state;
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

    public ThumbnailJobJpaEntity(ThumbnailJob job) {
        apply(job);
    }

    public ThumbnailJob toDomain() {
        return ThumbnailJob.reconstruct(mediaId, kind, ThumbnailJobStatus.from(state), attempts, availableAt, claimToken,
            leaseUntil, createdAt, completedAt, errorCode, thumbnailKey, previewKey);
    }

    public void apply(ThumbnailJob job) {
        mediaId = job.getMediaId();
        kind = job.getKind();
        state = job.getStatus().name();
        attempts = job.getAttempts();
        availableAt = job.getAvailableAt();
        claimToken = job.getClaimToken();
        leaseUntil = job.getLeaseUntil();
        createdAt = job.getCreatedAt();
        completedAt = job.getCompletedAt();
        errorCode = job.getErrorCode();
        thumbnailKey = job.getThumbnailKey();
        previewKey = job.getPreviewKey();
    }
}
