package com.sssok.renewal.incoming.organization;

import com.sssok.renewal.application.organization.job.OrganizeDispatchService;
import com.sssok.renewal.application.organization.job.OrganizeLivenessService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// 발송 대기·불확실 행을 다시 집고, 중단·유실된 실행을 실패 처리한다.
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "gallery.organization.job.enabled", havingValue = "true")
public class OrganizeJobSweeper {
    private final OrganizeDispatchService dispatchService;
    private final OrganizeLivenessService livenessService;

    @Scheduled(fixedDelayString = "${gallery.organization.job.dispatch.sweep-interval:10s}")
    public void sweep() {
        run("발송", dispatchService::dispatchDue);
        run("생존 감지", livenessService::sweep);
    }

    private void run(String name, Runnable task) {
        try {
            task.run();
        } catch (RuntimeException failure) {
            log.error("사진 정리 {} 스위프가 실패했습니다", name, failure);
        }
    }
}
