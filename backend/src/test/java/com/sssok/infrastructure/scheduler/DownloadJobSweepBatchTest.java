package com.sssok.infrastructure.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;

import com.sssok.application.download.SweepDownloadJobsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

// 배치가 정리 작업을 서비스에 위임하는지 확인한다. 스케줄 등록 여부는
// DownloadJobSweepBatchSchedulingTest 가 본다.
@SpringBootTest
@ActiveProfiles("test")
class DownloadJobSweepBatchTest {

    @Autowired
    DownloadJobSweepBatch downloadJobSweepBatch;

    @MockitoBean
    SweepDownloadJobsService sweepDownloadJobsService;

    @Test
    void 배치가_돌면_정리를_위임한다() {
        downloadJobSweepBatch.sweep();

        then(sweepDownloadJobsService).should().sweepExpired(any());
    }
}
