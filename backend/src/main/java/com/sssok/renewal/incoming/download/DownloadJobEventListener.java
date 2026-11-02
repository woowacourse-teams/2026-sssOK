package com.sssok.renewal.incoming.download;

import com.sssok.renewal.application.download.zip.DownloadCompressionWorker;
import com.sssok.renewal.application.download.zip.DownloadJobRequestedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// 커밋된 잡만 압축한다. spring.task.execution 풀(media-worker-*)은 빈 이름 applicationTaskExecutor 로 등록된다.
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
