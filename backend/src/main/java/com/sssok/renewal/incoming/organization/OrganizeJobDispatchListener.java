package com.sssok.renewal.incoming.organization;

import com.sssok.renewal.application.organization.job.OrganizeDispatchService;
import com.sssok.renewal.application.organization.job.OrganizeJobCreatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// 커밋된 작업만 보낸다. 이 시도가 실패하거나 서버가 멈춰도 스위퍼가 같은 실행을 다시 집어 간다.
@Component
@RequiredArgsConstructor
public class OrganizeJobDispatchListener {
    private final OrganizeDispatchService dispatchService;

    @Async("applicationTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(OrganizeJobCreatedEvent event) {
        dispatchService.dispatch(event.executionPk());
    }
}
