package com.sssok.application.download;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.sssok.application.port.out.DownloadJobRepository;
import com.sssok.application.port.out.FileStoragePort;
import com.sssok.domain.download.DownloadJob;
import com.sssok.domain.download.DownloadJobStatus;
import com.sssok.domain.file.StorageKey;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

// Repository + Service 통합 테스트 (H2). 스토리지 삭제는 목으로 둔다.
// @Transactional로 테스트마다 롤백한다 — 이 서비스는 status/ready_at만으로 대상을 고르기 때문에,
// 다른 테스트가 남긴 READY 잡이 격리 없이 섞이면 (특히 "미래" now를 넣는 테스트에서) 서로 오염된다.
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SweepDownloadJobsServiceTest {

    @Autowired
    SweepDownloadJobsService sweepDownloadJobsService;

    @Autowired
    DownloadJobRepository downloadJobRepository;

    @MockitoBean
    FileStoragePort fileStoragePort;

    private DownloadJob readyJobAt(Instant readyAt) {
        DownloadJob job = DownloadJob.create(1L, 100L, 1, 1024L, "sssOK_1.zip", Instant.now());
        job.markRunning();
        job.markReady(new StorageKey("rooms/1/downloads/" + System.nanoTime() + ".zip"), readyAt);
        return downloadJobRepository.save(job);
    }

    @Test
    void 보관_기간이_지난_READY_잡을_정리하고_실물을_지운다() {
        DownloadJob job = readyJobAt(Instant.now().minus(Duration.ofHours(2)));

        int swept = sweepDownloadJobsService.sweepExpired(Instant.now());

        assertThat(swept).isEqualTo(1);
        assertThat(downloadJobRepository.findById(job.getId()).orElseThrow().getStatus())
            .isEqualTo(DownloadJobStatus.EXPIRED);
        verify(fileStoragePort).delete(job.getZipStorageKey());
    }

    @Test
    void 보관_기간이_안_지난_READY_잡은_대상이_아니다() {
        DownloadJob job = readyJobAt(Instant.now());

        int swept = sweepDownloadJobsService.sweepExpired(Instant.now());

        assertThat(swept).isZero();
        assertThat(downloadJobRepository.findById(job.getId()).orElseThrow().getStatus())
            .isEqualTo(DownloadJobStatus.READY);
    }

    @Test
    void READY가_아니면_대상이_아니다() {
        DownloadJob job = downloadJobRepository.save(
            DownloadJob.create(1L, 100L, 1, 1024L, "sssOK_1.zip", Instant.now()));

        int swept = sweepDownloadJobsService.sweepExpired(Instant.now().plus(Duration.ofDays(1)));

        assertThat(swept).isZero();
        assertThat(downloadJobRepository.findById(job.getId()).orElseThrow().getStatus())
            .isEqualTo(DownloadJobStatus.QUEUED);
    }

    @Test
    void 다시_돌려도_같은_결과다() {
        readyJobAt(Instant.now().minus(Duration.ofHours(2)));

        assertThat(sweepDownloadJobsService.sweepExpired(Instant.now())).isEqualTo(1);
        assertThat(sweepDownloadJobsService.sweepExpired(Instant.now())).isZero();
    }
}
