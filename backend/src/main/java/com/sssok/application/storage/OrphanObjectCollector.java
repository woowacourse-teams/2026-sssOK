package com.sssok.application.storage;

import com.sssok.application.port.out.OrphanObjectRepository;
import com.sssok.domain.file.StorageKey;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// 정리 대기열에 대한 쓰기만 짧게 트랜잭션으로 감싼다.
// 트랜잭션을 열지 않는 PurgeOrphanObjectsService 안에서 직접 호출하면 프록시를 타지 않아
// @Transactional 이 걸리지 않으므로 별도 빈으로 둔다 (MediaFinisher 와 같은 이유).
@Component
@RequiredArgsConstructor
public class OrphanObjectCollector {

    private final OrphanObjectRepository orphanObjectRepository;

    // 삭제 트랜잭션 안에서 불리면 그 트랜잭션에 합류한다 — DB 행 삭제와 정리 대상 기록이
    // 함께 커밋되거나 함께 되돌아가야 하기 때문이다.
    @Transactional
    public void enqueue(List<StorageKey> storageKeys) {
        orphanObjectRepository.saveAll(storageKeys);
    }

    @Transactional
    public void markAttempted(List<Long> ids) {
        orphanObjectRepository.markAttempted(ids, Instant.now());
    }

    @Transactional
    public void markDone(List<StorageKey> storageKeys) {
        orphanObjectRepository.deleteAllByStorageKeyIn(storageKeys);
    }
}
