package com.sssok.renewal.incoming.media.thumbnail;

import com.sssok.renewal.application.media.thumbnail.ThumbnailJobService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "media.thumbnail.queue.backfill-enabled", havingValue = "true")
public class ThumbnailBackfill {
    private final ThumbnailJobService thumbnailJobService;
    private boolean completed;

    @Scheduled(fixedDelayString = "${media.thumbnail.queue.backfill-interval:10s}")
    public void backfill() {
        if (completed) { return; }
        List<Long> missing = thumbnailJobService.findMissingMediaIds();
        missing.forEach(thumbnailJobService::enqueueMissing);
        if (missing.isEmpty()) {
            completed = true;
            log.info("기존 PROCESSING 미디어의 썸네일 작업 보충 완료");
        }
    }
}
