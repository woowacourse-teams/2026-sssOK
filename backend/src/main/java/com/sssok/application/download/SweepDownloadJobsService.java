package com.sssok.application.download;

import com.sssok.application.port.out.DownloadJobRepository;
import com.sssok.domain.download.DownloadJob;
import com.sssok.infrastructure.config.DownloadProperties;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

// 보관 기간이 지난 READY 잡의 zip 실물을 지우고 EXPIRED로 넘긴다.
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
            // 한 잡이 실패해도 나머지는 계속 정리한다. 실패한 잡은 다음 회차에 다시 대상으로 잡힌다.
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
