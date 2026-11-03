package com.sssok.renewal.incoming.download;

import com.sssok.renewal.application.download.zip.SweepDownloadJobsService;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// 단일 인스턴스 전제로 분산 락이 없다 (OrphanObjectSweeper와 같은 전제).
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
