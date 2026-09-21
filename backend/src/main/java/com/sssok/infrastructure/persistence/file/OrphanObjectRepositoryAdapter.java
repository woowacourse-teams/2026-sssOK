package com.sssok.infrastructure.persistence.file;

import com.sssok.application.port.out.OrphanObjectRepository;
import com.sssok.domain.file.OrphanObject;
import com.sssok.domain.file.StorageKey;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrphanObjectRepositoryAdapter implements OrphanObjectRepository {

    private final OrphanObjectJpaRepository jpaRepository;

    @Override
    public void saveAll(List<StorageKey> storageKeys) {
        if (storageKeys.isEmpty()) {
            return;
        }
        jpaRepository.saveAll(storageKeys.stream()
            .distinct()
            .map(key -> new OrphanObjectJpaEntity(null, key.value(), 0, null))
            .toList());
    }

    @Override
    public List<OrphanObject> findStale(Instant retryBefore, int limit) {
        return jpaRepository.findStale(retryBefore, Limit.of(limit)).stream()
            .map(this::toDomain)
            .toList();
    }

    @Override
    public void markAttempted(List<Long> ids, Instant attemptedAt) {
        if (!ids.isEmpty()) {
            jpaRepository.markAttempted(ids, attemptedAt);
        }
    }

    @Override
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
