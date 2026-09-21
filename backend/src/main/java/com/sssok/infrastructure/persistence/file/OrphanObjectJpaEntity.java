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
@Table(name = "orphan_object")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrphanObjectJpaEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "storage_key", nullable = false)
    private String storageKey;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "last_attempted_at")
    private Instant lastAttemptedAt;

    public OrphanObjectJpaEntity(Long id, String storageKey, int attempts, Instant lastAttemptedAt) {
        this.id = id;
        this.storageKey = storageKey;
        this.attempts = attempts;
        this.lastAttemptedAt = lastAttemptedAt;
    }
}
