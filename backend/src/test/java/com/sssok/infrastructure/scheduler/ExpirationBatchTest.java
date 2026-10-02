package com.sssok.infrastructure.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;

import com.sssok.application.room.ExpireRoomsService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

// 배치가 스케줄에 등록되고, 만료 처리를 서비스에 위임하는지 확인한다.
@SpringBootTest
@ActiveProfiles("test")
class ExpirationBatchTest {

    @Autowired
    ExpirationBatch expirationBatch;

    @Autowired
    ApplicationContext applicationContext;

    @MockitoBean
    ExpireRoomsService expireRoomsService;

    @Test
    void 배치가_돌면_만료_처리를_위임한다() {
        expirationBatch.expire();

        then(expireRoomsService).should().expire(any());
    }

    @Test
    void 만료_배치가_스케줄에_등록된다() {
        assertThat(scheduledTaskNames())
            .anyMatch(name -> name.contains(ExpirationBatch.class.getName() + ".expire"));
    }

    // 스프링이 러너블을 감싸는 방식은 버전마다 달라서, 타입 대신 표기로 확인한다.
    private List<String> scheduledTaskNames() {
        return applicationContext.getBeansOfType(ScheduledTaskHolder.class).values().stream()
            .flatMap(holder -> holder.getScheduledTasks().stream())
            .map(task -> task.getTask().getRunnable().toString())
            .toList();
    }
}
