package com.sssok.renewal.application.search;

import com.sssok.renewal.outgoing.config.ImageSearchProperties;
import com.sssok.renewal.outgoing.database.MediaSearchDocumentRepository;
import java.time.Clock;
import java.util.concurrent.Semaphore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.TaskRejectedException;

/** 실행할 슬롯만 제출한다. 대기 작업은 DB에 두고 실행 중인 워커가 연속 선점한다. */
@Slf4j
public class ImageAnalysisDispatcher {
    private final AsyncTaskExecutor executor;
    private final AnalyzeImageService analyzer;
    private final MediaSearchDocumentRepository documents;
    private final ImageSearchProperties properties;
    private final Clock clock;
    private final Semaphore slots;

    public ImageAnalysisDispatcher(AsyncTaskExecutor executor, AnalyzeImageService analyzer,
        MediaSearchDocumentRepository documents, ImageSearchProperties properties, Clock clock) {
        this.executor = executor;
        this.analyzer = analyzer;
        this.documents = documents;
        this.properties = properties;
        this.clock = clock;
        this.slots = new Semaphore(properties.concurrency());
    }

    public void dispatch() {
        int budget = slots.availablePermits();
        for (int i = 0; i < budget && slots.tryAcquire(); i++) {
            boolean submitted = false;
            try {
                executor.execute(this::drain);
                submitted = true;
            } catch (TaskRejectedException rejected) {
                // DB 선점 전이므로 작업·시도 횟수는 바뀌지 않는다. 보조 폴링이 다시 깨운다.
                log.debug("이미지 분석 워커 제출 거절. 다음 배정에서 재시도합니다.");
                return;
            } finally {
                if (!submitted) { slots.release(); }
            }
        }
    }

    private void drain() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                var attempt = documents.claimNext(clock.instant(), properties.maxAttempts());
                if (attempt.isEmpty()) { return; }
                analyzer.analyze(attempt.get());
            }
        } catch (RuntimeException failure) {
            // 미선점 작업은 PENDING, 선점 후 중단된 작업은 기존 복구 정책으로 처리한다.
            log.warn("이미지 분석 워커 중단. 다음 배정에서 재개합니다.", failure);
        } finally {
            slots.release();
        }
    }
}
