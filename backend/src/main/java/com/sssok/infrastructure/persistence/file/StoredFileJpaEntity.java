package com.sssok.infrastructure.persistence.file;

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
@Table(name = "stored_file")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StoredFileJpaEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "room_id", nullable = false)
    private Long roomId;

    @Column(name = "uploader_id", nullable = false)
    private Long uploaderId;

    @Column(name = "original_file_name", nullable = false, length = 255)
    private String originalFileName;

    @Column(name = "media_type", nullable = false, length = 10)
    private String mediaType;

    @Column(name = "file_size_bytes", nullable = false)
    private Long fileSizeBytes;

    @Column(name = "storage_key", nullable = false, length = 255)
    private String storageKey;

    @Column(name = "folder_id")
    private Long folderId;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    // 서명 URL 을 마지막으로 내준 시각. 고아 정리 배치(#77)가 이 값을 기준으로 회수한다.
    // BaseEntity 의 createdAt 은 updatable = false 라 재발급 때 미룰 수 없어 따로 둔다.
    @Column(name = "reserved_at", nullable = false)
    private Instant reservedAt;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    public StoredFileJpaEntity(Long id, Long roomId, Long uploaderId, String originalFileName,
                               String mediaType, Long fileSizeBytes, String storageKey, Long folderId,
                               String status, Instant createdAt, Instant reservedAt, int retryCount) {
        super(createdAt);
        this.id = id;
        this.roomId = roomId;
        this.uploaderId = uploaderId;
        this.originalFileName = originalFileName;
        this.mediaType = mediaType;
        this.fileSizeBytes = fileSizeBytes;
        this.storageKey = storageKey;
        this.folderId = folderId;
        this.status = status;
        this.reservedAt = reservedAt;
        this.retryCount = retryCount;
    }
}
