package com.sssok.renewal.application.report;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// FeedbackNotificationTrigger 와 같은 이유로 커밋 뒤에, 다른 스레드에서 보낸다.
@Slf4j
@Component
public class MediaReportNotificationTrigger {

    private final MediaReportNotifier mediaReportNotifier;
    private final AsyncTaskExecutor taskExecutor;

    public MediaReportNotificationTrigger(MediaReportNotifier mediaReportNotifier,
                                          @Qualifier("applicationTaskExecutor") AsyncTaskExecutor taskExecutor) {
        this.mediaReportNotifier = mediaReportNotifier;
        this.taskExecutor = taskExecutor;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReported(MediaReportedEvent event) {
        try {
            taskExecutor.execute(() -> mediaReportNotifier.notifyReported(event.report()));
        } catch (TaskRejectedException e) {
            log.warn("신고 알림 작업을 제출하지 못해 건너뜁니다. reportId={}", event.report().getId());
        }
    }
}
