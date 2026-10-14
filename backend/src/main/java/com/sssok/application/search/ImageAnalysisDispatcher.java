package com.sssok.application.search;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.TaskRejectedException;

@Slf4j
@RequiredArgsConstructor
public class ImageAnalysisDispatcher {
    private final AsyncTaskExecutor executor;
    private final AnalyzeImageService analyzer;

    public void submit(Long mediaId) {
        try {
            executor.execute(() -> analyzer.analyze(mediaId));
        } catch (TaskRejectedException exception) {
            // 아직 선점하지 않았으므로 PENDING으로 남아 복구 배치가 다시 제출한다.
            log.warn("이미지 분석 제출 거절. 다음 배치에서 재시도합니다. mediaId={}", mediaId);
        }
    }
}
