package com.sssok.renewal.incoming.download;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;

import com.sssok.renewal.application.download.zip.SweepDownloadJobsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

// 배치가 정리 작업을 서비스에 위임하는지 확인한다. 스케줄 등록 여부는
// DownloadJobSweepBatchSchedulingTest 가 본다.
@ExtendWith(MockitoExtension.class)
class DownloadJobSweepBatchTest {

    @InjectMocks
    DownloadJobSweepBatch downloadJobSweepBatch;

    @Mock
    SweepDownloadJobsService sweepDownloadJobsService;

    @Test
    void 배치가_돌면_정리를_위임한다() {
        downloadJobSweepBatch.sweep();

        then(sweepDownloadJobsService).should().sweepExpired(any());
    }
}
