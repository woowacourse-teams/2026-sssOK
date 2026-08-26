package com.sssok.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import java.time.Instant;
import lombok.Getter;

// 생성·수정 시각은 도메인이 아니라 저장의 관심사라 JPA 엔티티에만 둔다.
// 도메인이 시각을 직접 다루는 경우는 그대로 도메인이 갖고,
// 어댑터가 넘긴 값을 여기서 덮어쓰지 않는다.
@Getter
@MappedSuperclass
public abstract class BaseEntity {

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected BaseEntity() {
    }

    protected BaseEntity(Instant createdAt) {
        this.createdAt = createdAt;
    }

    @PrePersist
    void onPersist() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
