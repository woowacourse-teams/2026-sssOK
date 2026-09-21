package com.sssok.infrastructure.scheduler;

import com.sssok.application.media.GenerateThumbnailService;
import com.sssok.application.port.out.FileRepository;
import com.sssok.application.port.out.FileRepository.StuckMedia;
import com.sssok.infrastructure.config.ThumbnailProperties;
import java.time.Instant;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// PROCESSING 에 갇힌 미디어를 다시 태우는 스케줄 배치.
//
// 썸네일은 등록 직후 비동기로 도는데, 그 사이 서버가 재시작되거나 스레드가 죽으면 작업이 사라진다.
// 그러면 그 사진은 영영 썸네일이 없고, 영상은 다운로드까지 409 로 막힌다. 이 배치가 유일한 회수 경로다.
//
// 직접 처리하지 않고 워커에 넘기는 이유: 배치 한 번에 집는 50개가 전부 영상이면 ffmpeg 제한
// 시간이 줄줄이 쌓여 스케줄 스레드가 묶이고, 그동안 다른 스케줄 작업까지 밀린다.
// 최초 등록과 같은 풀을 써, 영상은 영상 풀에서만 돌게 한다.
//
// 단일 인스턴스 전제로 분산 락이 없다 — PurgeBatch 와 같다.
@Slf4j
@Component
public class ThumbnailSweeper {

    private final FileRepository fileRepository;
    private final GenerateThumbnailService generateThumbnailService;
    private final ThumbnailProperties properties;
    private final AsyncTaskExecutor imageTaskExecutor;
    private final AsyncTaskExecutor videoTaskExecutor;

    public ThumbnailSweeper(
        FileRepository fileRepository,
        GenerateThumbnailService generateThumbnailService,
        ThumbnailProperties properties,
        @Qualifier("applicationTaskExecutor") AsyncTaskExecutor imageTaskExecutor,
        @Qualifier("videoThumbnailTaskExecutor") AsyncTaskExecutor videoTaskExecutor) {
        this.fileRepository = fileRepository;
        this.generateThumbnailService = generateThumbnailService;
        this.properties = properties;
        this.imageTaskExecutor = imageTaskExecutor;
        this.videoTaskExecutor = videoTaskExecutor;
    }

    @Scheduled(cron = "${media.thumbnail.sweep-cron:0 */5 * * * *}")
    public void sweep() {
        Instant stuckBefore = Instant.now().minus(properties.stuckAfter());
        List<StuckMedia> stuck = fileRepository.findStuckInProcessing(
            stuckBefore, properties.sweepBatchSize());
        if (stuck.isEmpty()) {
            return;
        }
        log.info("썸네일이 밀린 미디어 {}개를 다시 처리합니다", stuck.size());
        stuck.forEach(this::submit);
    }

    private void submit(StuckMedia media) {
        AsyncTaskExecutor taskExecutor = media.isVideo() ? videoTaskExecutor : imageTaskExecutor;
        try {
            taskExecutor.execute(() -> generateThumbnailService.generate(media.mediaId()));
        } catch (TaskRejectedException e) {
            // 풀이 이미 밀려 있다는 뜻이다. 행은 PROCESSING 그대로라 다음 배치가 다시 집어 간다.
            log.warn("밀린 썸네일 작업을 제출하지 못했습니다. 다음 배치에서 다시 시도합니다. mediaId={}",
                media.mediaId());
        }
    }
}
