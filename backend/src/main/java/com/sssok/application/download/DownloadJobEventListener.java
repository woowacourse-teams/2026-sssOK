package com.sssok.application.download;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// 압축 잡 생성 트랜잭션이 커밋된 뒤에만, 별도 스레드(media-worker 풀)에서 워커를 돌린다.
// SSE 이벤트(RoomEventListener 등)와 달리 여기서는 실제로 요청 스레드를 벗어나야 해서 @Async를 함께 쓴다 —
// 압축은 방 크기에 따라 오래 걸릴 수 있는 무거운 작업이라, 응답 스레드를 붙잡아두면 안 된다.
// application.yml의 spring.task.execution 풀(스레드 이름만 media-worker-*)이 스프링 부트가
// 자동 등록하는 빈 이름 "applicationTaskExecutor"로 노출되므로, @Async에는 그 이름을 지정한다.
@Component
@RequiredArgsConstructor
public class DownloadJobEventListener {

    private final DownloadCompressionWorker downloadCompressionWorker;

    @Async("applicationTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(DownloadJobRequestedEvent event) {
        downloadCompressionWorker.compress(event.jobId());
    }
}
