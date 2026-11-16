package com.sssok.renewal.application.download.zip;

import com.sssok.renewal.outgoing.monitoring.DownloadProcessingMetrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class DownloadCompressionDispatcher {

    static final String REJECTED_REASON = "요청이 많아 압축을 시작하지 못했습니다. 잠시 후 다시 요청해주세요";

    private final DownloadCompressionWorker downloadCompressionWorker;
    private final DownloadJobTransitions downloadJobTransitions;
    private final DownloadProcessingMetrics metrics;
    private final AsyncTaskExecutor taskExecutor;

    public DownloadCompressionDispatcher(DownloadCompressionWorker downloadCompressionWorker,
                                         DownloadJobTransitions downloadJobTransitions,
                                         DownloadProcessingMetrics metrics,
                                         @Qualifier("downloadCompressionTaskExecutor") AsyncTaskExecutor taskExecutor) {
        this.downloadCompressionWorker = downloadCompressionWorker;
        this.downloadJobTransitions = downloadJobTransitions;
        this.metrics = metrics;
        this.taskExecutor = taskExecutor;
    }

    public void dispatch(Long jobId) {
        try {
            taskExecutor.execute(() -> downloadCompressionWorker.compress(jobId));
        } catch (TaskRejectedException e) {
            log.warn("압축 실행기가 가득 차 작업을 받지 못했습니다. jobId={}", jobId);
            metrics.rejected();
            downloadJobTransitions.markRejected(jobId, REJECTED_REASON);
        }
    }
}
