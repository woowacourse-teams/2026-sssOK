package com.sssok.application.feedback;

import com.sssok.application.port.out.FeedbackNotifierPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// 의견 등록이 커밋된 뒤에 팀 알림을 띄운다.
//
// AFTER_COMMIT 인 이유: 롤백된 의견을 알리면 채널에는 있는데 관리자 화면에는 없는 의견이 생긴다.
// 다른 스레드로 넘기는 이유: 사용자 기준 등록 완료는 DB 행이 저장된 시점이다. 디스코드가 느리거나
// 죽어 있다고 201 응답이 늦어지거나 실패하면 주객이 전도된다.
//
// @Async 대신 실행기를 직접 받는 이유는 StorageCleanupTrigger 와 같다 — 공용 풀이 가득 차 제출이
// 거부되는 경우를 여기서 잡아 로그로 남긴다. 알림은 다시 보내지 않는다 (재시도하면 중복 알림을
// 함께 다뤄야 한다). 의견 자체는 저장돼 있어 관리자 조회로 확인할 수 있다.
@Slf4j
@Component
public class FeedbackNotificationTrigger {

    private final FeedbackNotifierPort feedbackNotifier;
    private final AsyncTaskExecutor taskExecutor;

    public FeedbackNotificationTrigger(FeedbackNotifierPort feedbackNotifier,
                                       @Qualifier("applicationTaskExecutor") AsyncTaskExecutor taskExecutor) {
        this.feedbackNotifier = feedbackNotifier;
        this.taskExecutor = taskExecutor;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onFeedbackCreated(FeedbackCreatedEvent event) {
        try {
            taskExecutor.execute(() -> feedbackNotifier.notifyCreated(event.feedback()));
        } catch (TaskRejectedException e) {
            log.warn("의견 알림 작업을 제출하지 못해 건너뜁니다. feedbackId={}", event.feedback().getId());
        }
    }
}
