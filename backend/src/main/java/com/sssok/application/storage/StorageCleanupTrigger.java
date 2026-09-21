package com.sssok.application.storage;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// 삭제가 커밋된 뒤에 오브젝트 정리를 띄운다.
//
// AFTER_COMMIT 인 이유: 커밋 전에 지우면 이후 롤백됐을 때 DB 에는 살아 있는데 실물은 사라진
// 미디어가 남는다. 되돌릴 방법이 없는 쪽을 나중에 한다.
// @Async 인 이유: 사용자 기준 삭제 완료는 DB 행이 지워진 시점이다. 응답이 스토리지 왕복을
// 기다릴 이유가 없다.
//
// 끌 수 있게 해둔 이유: 협력 객체를 목으로 둔 테스트에서 이 스레드가 같은 목을 동시에 건드리면
// 검증이 간헐적으로 깨진다 (ThumbnailTrigger 와 같다). 워커 자체는 PurgeOrphanObjectsService 를
// 직접 불러 검증한다.
@Component
@ConditionalOnProperty(name = "storage.cleanup.auto-purge", havingValue = "true",
    matchIfMissing = true)
@RequiredArgsConstructor
public class StorageCleanupTrigger {

    private final PurgeOrphanObjectsService purgeOrphanObjectsService;

    @Async("applicationTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onObjectsOrphaned(ObjectsOrphanedEvent event) {
        purgeOrphanObjectsService.purge(event.storageKeys());
    }
}
