package com.sssok.infrastructure.scheduler;

import com.sssok.application.port.out.OrphanObjectRepository;
import com.sssok.application.storage.OrphanObjectCollector;
import com.sssok.application.storage.PurgeOrphanObjectsService;
import com.sssok.domain.file.OrphanObject;
import com.sssok.infrastructure.config.StorageCleanupProperties;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// 정리되지 않고 남은 오브젝트를 다시 지우는 스케줄 배치.
//
// 삭제 직후의 정리는 비동기로 도는데, 그 사이 서버가 재시작되거나 R2 가 잠시 응답하지 않으면
// 작업이 사라진다. 그러면 그 오브젝트는 주인 없이 영영 요금을 먹는다. 이 배치가 유일한 회수 경로다.
//
// 단일 인스턴스 전제로 분산 락이 없다 — PurgeBatch, ThumbnailSweeper 와 같다.
@Slf4j
@Component
@RequiredArgsConstructor
public class OrphanObjectSweeper {

    private final OrphanObjectRepository orphanObjectRepository;
    private final OrphanObjectCollector orphanObjectCollector;
    private final PurgeOrphanObjectsService purgeOrphanObjectsService;
    private final StorageCleanupProperties properties;

    @Scheduled(cron = "${storage.cleanup.sweep-cron:0 */10 * * * *}")
    public void sweep() {
        Instant retryBefore = Instant.now().minus(properties.retryAfter());
        List<OrphanObject> stale =
            orphanObjectRepository.findStale(retryBefore, properties.sweepBatchSize());
        if (stale.isEmpty()) {
            return;
        }
        log.info("정리되지 않은 오브젝트 {}개를 다시 지웁니다", stale.size());

        // 지우기 전에 시도를 먼저 기록한다. 계속 실패하는 키가 매 회차 맨 앞에 남아
        // 뒤에 쌓인 것들을 굶기지 않게 하려는 것이다.
        orphanObjectCollector.markAttempted(stale.stream().map(OrphanObject::id).toList());
        purgeOrphanObjectsService.purge(stale.stream().map(OrphanObject::storageKey).toList());
    }
}
