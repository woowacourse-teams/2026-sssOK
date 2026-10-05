package com.sssok.infrastructure.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;

import com.sssok.application.room.ExpireRoomsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

// 배치가 만료 처리를 서비스에 위임하는지 확인한다. 스케줄 등록 여부는
// ExpirationBatchSchedulingTest 가 본다.
@ExtendWith(MockitoExtension.class)
class ExpirationBatchTest {

    @InjectMocks
    ExpirationBatch expirationBatch;

    @Mock
    ExpireRoomsService expireRoomsService;

    @Test
    void 배치가_돌면_만료_처리를_위임한다() {
        expirationBatch.expire();

        then(expireRoomsService).should().expire(any());
    }
}
