package com.sssok.renewal.incoming.media.thumbnail;

import com.sssok.renewal.application.media.thumbnail.ThumbnailDispatcher;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "media.thumbnail.auto-generate", havingValue = "true", matchIfMissing = true)
public class ThumbnailDispatchScheduler {
    private final ThumbnailDispatcher thumbnailDispatcher;

    @Scheduled(fixedDelayString = "${media.thumbnail.queue.dispatch-interval:1s}")
    public void dispatchPendingJobs() {
        thumbnailDispatcher.dispatchPendingJobs();
    }
}
