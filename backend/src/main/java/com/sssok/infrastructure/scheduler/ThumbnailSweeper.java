package com.sssok.infrastructure.scheduler;

import com.sssok.application.media.GenerateThumbnailService;
import com.sssok.application.port.out.FileRepository;
import com.sssok.infrastructure.config.ThumbnailProperties;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// PROCESSING 에 갇힌 미디어를 다시 태우는 스케줄 배치.
//
// 썸네일은 등록 직후 비동기로 도는데, 그 사이 서버가 재시작되거나 스레드가 죽으면 작업이 사라진다.
// 그러면 그 사진은 영영 썸네일이 없고, 영상은 다운로드까지 409 로 막힌다. 이 배치가 유일한 회수 경로다.
//
// 단일 인스턴스 전제로 분산 락이 없다 — PurgeBatch 와 같다.
@Slf4j
@Component
@RequiredArgsConstructor
public class ThumbnailSweeper {

    private final FileRepository fileRepository;
    private final GenerateThumbnailService generateThumbnailService;
    private final ThumbnailProperties properties;

    @Scheduled(cron = "${media.thumbnail.sweep-cron:0 */5 * * * *}")
    public void sweep() {
        Instant stuckBefore = Instant.now().minus(properties.stuckAfter());
        List<Long> stuck = fileRepository.findStuckInProcessing(
            stuckBefore, properties.sweepBatchSize());
        if (stuck.isEmpty()) {
            return;
        }
        log.info("썸네일이 밀린 미디어 {}개를 다시 처리합니다", stuck.size());
        stuck.forEach(generateThumbnailService::generate);
    }
}
