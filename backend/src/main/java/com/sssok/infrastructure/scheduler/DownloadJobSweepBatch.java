package com.sssok.infrastructure.scheduler;

import com.sssok.application.download.SweepDownloadJobsService;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// 보관 기간이 지난 다운로드 zip을 영구 삭제하는 스케줄 배치.
// 단일 인스턴스 전제로 분산 락이 없다 (PurgeBatch와 같은 전제).
@Slf4j
@Component
@RequiredArgsConstructor
public class DownloadJobSweepBatch {

    private final SweepDownloadJobsService sweepDownloadJobsService;

    @Scheduled(cron = "${download.sweep-cron:0 */10 * * * *}")
    public void sweep() {
        int swept = sweepDownloadJobsService.sweepExpired(Instant.now());
        if (swept > 0) {
            log.info("보관 기간이 지난 다운로드 zip {}개를 정리했습니다", swept);
        }
    }
}
