package com.sssok.application.download;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sssok.application.port.out.DownloadJobRepository;
import com.sssok.domain.download.DownloadJob;
import com.sssok.domain.download.DownloadJobStatus;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

// Repository + Service 통합 테스트 (H2). 워커를 직접(동기) 호출한다 —
// 비동기 배선 자체는 DownloadJobAsyncTriggerTest 에서 별도로 검증한다.
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DownloadCompressionWorkerTest {

    private static final Long ROOM_ID = 1L;
    private static final Long REQUESTER_ID = 100L;

    @Autowired
    DownloadCompressionWorker downloadCompressionWorker;

    @Autowired
    DownloadJobRepository downloadJobRepository;

    @Test
    void QUEUED_잡을_압축하면_RUNNING으로_전이한다() {
        DownloadJob job = downloadJobRepository.save(
            DownloadJob.create(ROOM_ID, REQUESTER_ID, 1, 1024L, "sssOK_1.zip", Instant.now()));

        downloadCompressionWorker.compress(job.getId());

        DownloadJob updated = downloadJobRepository.findById(job.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(DownloadJobStatus.RUNNING);
    }

    @Test
    void 없는_잡을_압축하려_하면_예외() {
        assertThatThrownBy(() -> downloadCompressionWorker.compress(999L))
            .isInstanceOf(IllegalStateException.class);
    }
}
