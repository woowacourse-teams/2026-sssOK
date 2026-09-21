package com.sssok.application.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// 삭제가 커밋된 뒤에 오브젝트 정리를 띄운다.
//
// AFTER_COMMIT 인 이유: 커밋 전에 지우면 이후 롤백됐을 때 DB 에는 살아 있는데 실물은 사라진
// 미디어가 남는다. 되돌릴 방법이 없는 쪽을 나중에 한다.
// 다른 스레드로 넘기는 이유: 사용자 기준 삭제 완료는 DB 행이 지워진 시점이다. 응답이 스토리지
// 왕복을 기다릴 이유가 없다.
//
// @Async 대신 실행기를 직접 받는 이유: 이 풀은 썸네일·zip 워커와 공용이라 가득 찰 수 있고,
// 그때 작업 제출은 TaskRejectedException 으로 거부된다. @Async 는 제출이 프록시 안에서
// 일어나 이 거부를 잡을 자리가 없다. 거부는 실패가 아니라 "나중에 한다"로 다뤄야 하는 경우다
// — 대기열 행은 이미 커밋돼 있어 OrphanObjectSweeper 가 그대로 회수한다.
//
// 끌 수 있게 해둔 이유: 협력 객체를 목으로 둔 테스트에서 이 스레드가 같은 목을 동시에 건드리면
// 검증이 간헐적으로 깨진다 (ThumbnailTrigger 와 같다). 워커 자체는 PurgeOrphanObjectsService 를
// 직접 불러 검증한다.
@Slf4j
@Component
@ConditionalOnProperty(name = "storage.cleanup.auto-purge", havingValue = "true",
    matchIfMissing = true)
public class StorageCleanupTrigger {

    private final PurgeOrphanObjectsService purgeOrphanObjectsService;
    private final AsyncTaskExecutor taskExecutor;

    // application.yml 의 spring.task.execution 풀이 스프링 부트가 자동 등록하는 빈 이름
    // "applicationTaskExecutor" 로 노출된다 (DownloadJobEventListener 와 같은 풀).
    public StorageCleanupTrigger(PurgeOrphanObjectsService purgeOrphanObjectsService,
                                 @Qualifier("applicationTaskExecutor") AsyncTaskExecutor taskExecutor) {
        this.purgeOrphanObjectsService = purgeOrphanObjectsService;
        this.taskExecutor = taskExecutor;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onObjectsOrphaned(ObjectsOrphanedEvent event) {
        try {
            taskExecutor.execute(() -> purgeOrphanObjectsService.purge(event.storageKeys()));
        } catch (TaskRejectedException e) {
            // 삭제 자체는 이미 커밋됐다. 지금 못 지운 것은 대기열에 남아 있으므로 배치가 맡는다.
            log.warn("정리 작업을 제출하지 못했습니다. 회수 배치가 대신 지웁니다. keys={}",
                event.storageKeys().size());
        }
    }
}
