package com.sssok.renewal.application.download.zip;

import com.sssok.renewal.outgoing.monitoring.DownloadProcessingMetrics;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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

    // 이 서버가 맡았지만 아직 끝나지 않은 잡(대기열·실행 중). 종료할 때 남은 잡을 닫는 기준이다.
    // 블루-그린 두 칸이 같은 DB 를 쓰므로 DB 상태가 아니라 이 서버가 맡은 잡만 본다.
    private final Set<Long> inFlightJobIds = ConcurrentHashMap.newKeySet();

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
        inFlightJobIds.add(jobId);
        try {
            taskExecutor.execute(() -> compress(jobId));
        } catch (TaskRejectedException e) {
            inFlightJobIds.remove(jobId);
            log.warn("압축 실행기가 가득 차 작업을 받지 못했습니다. jobId={}", jobId);
            metrics.rejected();
            downloadJobTransitions.markRejected(jobId, REJECTED_REASON);
        }
    }

    Set<Long> inFlightJobIds() {
        return Set.copyOf(inFlightJobIds);
    }

    private void compress(Long jobId) {
        try {
            downloadCompressionWorker.compress(jobId);
        } finally {
            inFlightJobIds.remove(jobId);
        }
    }
}
