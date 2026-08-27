package com.sssok.infrastructure.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import org.springframework.test.context.ActiveProfiles;

// 배치가 실제로 스케줄에 등록되는지 확인한다. @EnableScheduling 이 빠지면 여기서 걸린다.
@SpringBootTest
@ActiveProfiles("test")
class DownloadJobSweepBatchSchedulingTest {

    @Autowired
    ApplicationContext applicationContext;

    @Test
    void 정리_배치가_스케줄에_등록된다() {
        assertThat(scheduledTaskNames())
            .anyMatch(name -> name.contains(DownloadJobSweepBatch.class.getName() + ".sweep"));
    }

    // 스프링이 러너블을 감싸는 방식은 버전마다 달라서, 타입 대신 표기로 확인한다.
    private List<String> scheduledTaskNames() {
        return applicationContext.getBeansOfType(ScheduledTaskHolder.class).values().stream()
            .flatMap(holder -> holder.getScheduledTasks().stream())
            .map(task -> task.getTask().getRunnable().toString())
            .toList();
    }
}
