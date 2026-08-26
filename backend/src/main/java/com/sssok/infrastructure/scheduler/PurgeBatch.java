package com.sssok.infrastructure.scheduler;

import com.sssok.application.room.PurgeRoomService;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// 보관 기간이 지난 데이터를 영구 삭제하는 스케줄 배치.
// 단일 인스턴스 전제로 분산 락이 없다
@Slf4j
@Component
@RequiredArgsConstructor
public class PurgeBatch {

    private final PurgeRoomService purgeRoomService;

    @Scheduled(cron = "${room.purge-cron:0 0 4 * * *}")
    public void purge() {
        int purged = purgeRoomService.purgeAll(Instant.now());
        if (purged > 0) {
            log.info("보존 기간이 지난 방 {}개를 영구 삭제했습니다", purged);
        }
    }
}
