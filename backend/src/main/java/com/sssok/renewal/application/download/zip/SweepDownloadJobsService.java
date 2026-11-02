package com.sssok.renewal.application.download.zip;

import com.sssok.renewal.outgoing.database.DownloadJobRepository;
import com.sssok.renewal.domain.download.DownloadJob;
import com.sssok.renewal.outgoing.config.DownloadProperties;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SweepDownloadJobsService {

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
}
