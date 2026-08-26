package com.sssok.infrastructure.persistence.room;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "room")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RoomJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @Column(nullable = false, unique = true, length = 8)
    private String code;

    @Column(nullable = false, length = 12)
    private String name;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "upload_policy", nullable = false, length = 20)
    private String uploadPolicy;

    @Column(name = "host_id", nullable = false)
    private Long hostId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public RoomJpaEntity(
        Long id,
        Long version,
        String code,
        String name,
        String status,
        Instant expiresAt,
        String uploadPolicy,
        Long hostId,
        Instant createdAt,
        Instant deletedAt
    ) {
        this.id = id;
        this.version = version;
        this.code = code;
        this.name = name;
        this.status = status;
        this.expiresAt = expiresAt;
        this.uploadPolicy = uploadPolicy;
        this.hostId = hostId;
        this.createdAt = createdAt;
        this.deletedAt = deletedAt;
    }
}
