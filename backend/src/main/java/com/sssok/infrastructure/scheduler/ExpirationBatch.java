package com.sssok.infrastructure.scheduler;

import com.sssok.application.room.ExpireRoomsService;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// 만료 시각이 지난 방의 저장된 상태를 EXPIRED 로 바꾸는 스케줄 배치.
// 단일 인스턴스 전제로 분산 락이 없다 (DownloadJobSweepBatch 와 같은 전제).
@Slf4j
@Component
@RequiredArgsConstructor
public class ExpirationBatch {

    private final ExpireRoomsService expireRoomsService;

    @Scheduled(cron = "${room.expiration-cron:0 */10 * * * *}")
    public void expire() {
        int expired = expireRoomsService.expire(Instant.now());
        if (expired > 0) {
            log.info("만료 시각이 지난 방 {}개를 만료 처리했습니다", expired);
        }
    }
}
