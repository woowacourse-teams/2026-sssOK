package com.sssok.renewal.outgoing.database;

import com.sssok.infrastructure.persistence.file.OrphanObjectJpaEntity;
import com.sssok.infrastructure.persistence.file.OrphanObjectJpaRepository;
import com.sssok.domain.file.OrphanObject;
import com.sssok.domain.file.StorageKey;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrphanObjectRepository {

    private final OrphanObjectJpaRepository jpaRepository;

    public void saveAll(List<StorageKey> storageKeys) {
        if (storageKeys.isEmpty()) {
            return;
        }
        jpaRepository.saveAll(storageKeys.stream()
            .distinct()
            .map(key -> new OrphanObjectJpaEntity(null, key.value(), 0, null))
            .toList());
    }

    public List<OrphanObject> findStale(Instant retryBefore, int limit) {
        return jpaRepository.findStale(retryBefore, Limit.of(limit)).stream()
            .map(this::toDomain)
            .toList();
    }

    public void markAttempted(List<Long> ids, Instant attemptedAt) {
        if (!ids.isEmpty()) {
            jpaRepository.markAttempted(ids, attemptedAt);
        }
    }

    public void deleteAllByStorageKeyIn(List<StorageKey> storageKeys) {
        if (!storageKeys.isEmpty()) {
            jpaRepository.deleteByStorageKeyIn(storageKeys.stream().map(StorageKey::value).toList());
        }
    }

    private OrphanObject toDomain(OrphanObjectJpaEntity entity) {
        return new OrphanObject(entity.getId(), new StorageKey(entity.getStorageKey()),
            entity.getAttempts(), entity.getLastAttemptedAt());
    }
}
