package com.sssok.infrastructure.persistence.file;

import com.sssok.infrastructure.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
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

    // 썸네일·최적화 워커가 채운다. 그 전까지는 비어 있고, 영상은 끝까지 비어 있다.
    @Column(name = "thumbnail_key", length = 255)
    private String thumbnailKey;

    @Column(name = "width")
    private Integer width;

    @Column(name = "height")
    private Integer height;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    // 원본 EXIF 에서 읽는다. 카메라가 남기지 않았으면 비어 있다.
    @Column(name = "taken_at")
    private Instant takenAt;

    // 소수점 자릿수가 정확도를 좌우해 부동소수로 두지 않는다.
    @Column(name = "latitude", precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 9, scale = 6)
    private BigDecimal longitude;

    public StoredFileJpaEntity(Long id, Long roomId, Long uploaderId, String originalFileName,
                               String mediaType, Long fileSizeBytes, String storageKey, Long folderId,
                               String status, Instant createdAt, Instant reservedAt, int retryCount,
                               String thumbnailKey, Integer width, Integer height,
                               Instant takenAt, BigDecimal latitude, BigDecimal longitude) {
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
        this.thumbnailKey = thumbnailKey;
        this.width = width;
        this.height = height;
        this.takenAt = takenAt;
        this.latitude = latitude;
        this.longitude = longitude;
    }
}
