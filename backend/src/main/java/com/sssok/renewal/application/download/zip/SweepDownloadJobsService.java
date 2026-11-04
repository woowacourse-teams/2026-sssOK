package com.sssok.renewal.application.download.zip;

import com.sssok.renewal.outgoing.database.DownloadJobRepository;
import com.sssok.renewal.domain.download.DownloadJob;
import com.sssok.renewal.outgoing.config.DownloadProperties;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SweepDownloadJobsService {

    // 압축 도중 서버가 꺼져 멈춘 잡은 사용자당 동시 잡 수에 계속 잡혀 새 다운로드를 막으므로 실패로 닫는다.
    static final String STALE_FAILURE_REASON = "압축이 중단되었습니다. 다시 요청해주세요";

    private final DownloadJobRepository downloadJobRepository;
    private final DownloadJobExpirer downloadJobExpirer;
    private final DownloadProperties downloadProperties;

    public int sweepExpired(Instant now) {
        Instant threshold = now.minus(downloadProperties.retention());
        List<DownloadJob> targets = downloadJobRepository.findAllExpiredReady(threshold);

        int swept = 0;
        for (DownloadJob job : targets) {
            // 실패한 잡은 READY 로 남아 다음 회차에 다시 잡힌다.
            try {
                downloadJobExpirer.expire(job);
                swept++;
            } catch (RuntimeException e) {
                log.warn("다운로드 잡 {} 정리에 실패했습니다. 다음 회차에 다시 시도합니다", job.getId(), e);
            }
        }
        return swept;
    }

    @Transactional
    public int failStale(Instant now) {
        return downloadJobRepository.failStaleActive(now.minus(downloadProperties.staleAfter()), STALE_FAILURE_REASON);
    }
}
