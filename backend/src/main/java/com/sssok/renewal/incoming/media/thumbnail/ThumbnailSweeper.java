package com.sssok.renewal.incoming.media.thumbnail;

import com.sssok.renewal.application.media.thumbnail.ThumbnailJobCompletionService;
import java.util.List;
import com.sssok.renewal.outgoing.monitoring.MediaProcessingMetrics;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// 작업 나이가 아닌 임대 만료로 죽은 워커를 회수한다. 실행은 디스패처만 담당한다.
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "media.thumbnail.auto-generate", havingValue = "true", matchIfMissing = true)
public class ThumbnailSweeper {
    private final ThumbnailJobCompletionService thumbnailJobCompletionService;
    private final MediaProcessingMetrics metrics;

    @Scheduled(fixedDelayString = "${media.thumbnail.queue.recovery-interval:10s}")
    public void sweep() {
        List<Long> expiredMediaIds = thumbnailJobCompletionService.findExpiredMediaIds();
        expiredMediaIds.forEach(thumbnailJobCompletionService::recoverExpired);
        metrics.swept(expiredMediaIds.size());
    }
}
