package com.sssok.renewal.incoming.download;

import com.sssok.renewal.application.download.zip.DownloadCompressionDispatcher;
import com.sssok.renewal.application.download.zip.DownloadJobRequestedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// 커밋된 잡만 압축한다.
@Component
@RequiredArgsConstructor
public class DownloadJobEventListener {

    private final DownloadCompressionDispatcher downloadCompressionDispatcher;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(DownloadJobRequestedEvent event) {
        downloadCompressionDispatcher.dispatch(event.jobId());
    }
}
