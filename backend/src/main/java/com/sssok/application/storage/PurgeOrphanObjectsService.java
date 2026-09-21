package com.sssok.application.storage;

import com.sssok.application.port.out.FileStoragePort;
import com.sssok.domain.file.StorageKey;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

// 대기열에 올라온 오브젝트를 실제로 지우는 워커.
//
// 트랜잭션을 열지 않는다. 스토리지 왕복 동안 DB 커넥션을 쥐고 있으면 삭제 몇 건만으로 풀이 마른다
// — 삭제를 트랜잭션 밖으로 뺀 이유 자체가 그것이다. 대기열 정리만 OrphanObjectCollector 가 짧게 연다.
@Slf4j
@Service
@RequiredArgsConstructor
public class PurgeOrphanObjectsService {

    private final FileStoragePort fileStoragePort;
    private final OrphanObjectCollector orphanObjectCollector;

    public void purge(List<StorageKey> storageKeys) {
        if (storageKeys.isEmpty()) {
            return;
        }
        try {
            removeDeleted(storageKeys, fileStoragePort.deleteAll(storageKeys));
        } catch (RuntimeException e) {
            // 여기서 예외가 올라가 봐야 받을 곳이 없다(@Async 스레드이거나 스케줄 배치다).
            // 대기열 행을 그대로 두면 OrphanObjectSweeper 가 다시 태운다.
            log.warn("오브젝트 정리에 실패했습니다. 회수 배치가 다시 시도합니다. keys={}",
                storageKeys.size(), e);
        }
    }

    // 지우지 못한 키의 행은 남겨야 다음 회차가 다시 집는다.
    private void removeDeleted(List<StorageKey> requested, List<StorageKey> failed) {
        if (failed.isEmpty()) {
            orphanObjectCollector.markDone(requested);
            return;
        }
        Set<StorageKey> notDeleted = new HashSet<>(failed);
        orphanObjectCollector.markDone(requested.stream()
            .filter(key -> !notDeleted.contains(key))
            .toList());
        log.warn("오브젝트 {}개를 지우지 못해 정리 대기열에 남겨둡니다", failed.size());
    }
}
