package com.sssok.infrastructure.persistence.download;

import com.sssok.infrastructure.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "download_job")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DownloadJobJpaEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "room_id", nullable = false)
    private Long roomId;

    @Column(name = "requester_id", nullable = false)
    private Long requesterId;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "media_count", nullable = false)
    private int mediaCount;

    @Column(name = "total_size_bytes", nullable = false)
    private long totalSizeBytes;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "zip_storage_key", length = 255)
    private String zipStorageKey;

    @Column(name = "progress", nullable = false)
    private int progress;

    @Column(name = "ready_at")
    private Instant readyAt;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    public DownloadJobJpaEntity(Long id, Long roomId, Long requesterId, String status,
                                int mediaCount, long totalSizeBytes, String fileName,
                                String zipStorageKey, int progress, Instant readyAt,
                                String failureReason, Instant createdAt) {
        super(createdAt);
        this.id = id;
        this.roomId = roomId;
        this.requesterId = requesterId;
        this.status = status;
        this.mediaCount = mediaCount;
        this.totalSizeBytes = totalSizeBytes;
        this.fileName = fileName;
        this.zipStorageKey = zipStorageKey;
        this.progress = progress;
        this.readyAt = readyAt;
        this.failureReason = failureReason;
    }
}
