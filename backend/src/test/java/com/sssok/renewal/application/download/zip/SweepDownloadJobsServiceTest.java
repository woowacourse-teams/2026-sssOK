package com.sssok.renewal.application.download.zip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.sssok.renewal.outgoing.database.DownloadJobRepository;
import com.sssok.renewal.outgoing.storage.FileStorage;
import com.sssok.renewal.domain.download.DownloadJob;
import com.sssok.renewal.domain.download.status.ExpiredDownloadJobStatus;
import com.sssok.renewal.domain.download.status.FailedDownloadJobStatus;
import com.sssok.renewal.domain.download.status.QueuedDownloadJobStatus;
import com.sssok.renewal.domain.download.status.ReadyDownloadJobStatus;
import com.sssok.renewal.domain.download.status.RunningDownloadJobStatus;
import com.sssok.domain.file.StorageKey;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import com.sssok.support.H2IntegrationTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

// Repository + Service 통합 테스트 (H2). 스토리지 삭제는 목으로 둔다.
// @Transactional로 테스트마다 롤백한다 — 이 서비스는 status/ready_at만으로 대상을 고르기 때문에,
// 다른 테스트가 남긴 READY 잡이 격리 없이 섞이면 (특히 "미래" now를 넣는 테스트에서) 서로 오염된다.
@H2IntegrationTest
@Transactional
class SweepDownloadJobsServiceTest {

    @Autowired
    SweepDownloadJobsService sweepDownloadJobsService;

    @Autowired
    DownloadJobRepository downloadJobRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @MockitoBean
    FileStorage fileStorage;

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
            .isEqualTo(ExpiredDownloadJobStatus.INSTANCE);
        verify(fileStorage).delete(job.getZipStorageKey());
    }

    @Test
    void 보관_기간이_안_지난_READY_잡은_대상이_아니다() {
        DownloadJob job = readyJobAt(Instant.now());

        int swept = sweepDownloadJobsService.sweepExpired(Instant.now());

        assertThat(swept).isZero();
        assertThat(downloadJobRepository.findById(job.getId()).orElseThrow().getStatus())
            .isEqualTo(ReadyDownloadJobStatus.INSTANCE);
    }

    @Test
    void READY가_아니면_대상이_아니다() {
        DownloadJob job = downloadJobRepository.save(
            DownloadJob.create(1L, 100L, 1, 1024L, "sssOK_1.zip", Instant.now()));

        int swept = sweepDownloadJobsService.sweepExpired(Instant.now().plus(Duration.ofDays(1)));

        assertThat(swept).isZero();
        assertThat(downloadJobRepository.findById(job.getId()).orElseThrow().getStatus())
            .isEqualTo(QueuedDownloadJobStatus.INSTANCE);
    }

    @Test
    void 다시_돌려도_같은_결과다() {
        readyJobAt(Instant.now().minus(Duration.ofHours(2)));

        assertThat(sweepDownloadJobsService.sweepExpired(Instant.now())).isEqualTo(1);
        assertThat(sweepDownloadJobsService.sweepExpired(Instant.now())).isZero();
    }

    @Test
    void 진행이_멈춘_QUEUED와_RUNNING_잡을_실패로_닫는다() {
        DownloadJob queued = downloadJobRepository.save(
            DownloadJob.create(1L, 100L, 1, 1024L, "sssOK_1.zip", Instant.now()));
        DownloadJob running = DownloadJob.create(1L, 100L, 1, 1024L, "sssOK_1.zip", Instant.now());
        running.markRunning();
        running = downloadJobRepository.save(running);
        lastProgressAt(queued, Instant.now().minus(Duration.ofHours(1)));
        lastProgressAt(running, Instant.now().minus(Duration.ofHours(1)));

        int failed = sweepDownloadJobsService.failStale(Instant.now());

        assertThat(failed).isEqualTo(2);
        DownloadJob reloaded = downloadJobRepository.findById(running.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(FailedDownloadJobStatus.INSTANCE);
        assertThat(reloaded.getFailureReason()).isEqualTo(SweepDownloadJobsService.STALE_FAILURE_REASON);
        assertThat(downloadJobRepository.findById(queued.getId()).orElseThrow().getStatus())
            .isEqualTo(FailedDownloadJobStatus.INSTANCE);
    }

    @Test
    void 최근에_진행한_잡은_닫지_않는다() {
        DownloadJob running = DownloadJob.create(1L, 100L, 1, 1024L, "sssOK_1.zip", Instant.now());
        running.markRunning();
        running = downloadJobRepository.save(running);
        lastProgressAt(running, Instant.now().minus(Duration.ofMinutes(5)));

        int failed = sweepDownloadJobsService.failStale(Instant.now());

        assertThat(failed).isZero();
        assertThat(downloadJobRepository.findById(running.getId()).orElseThrow().getStatus())
            .isEqualTo(RunningDownloadJobStatus.INSTANCE);
    }

    @Test
    void 끝난_잡은_오래돼도_닫지_않는다() {
        DownloadJob ready = readyJobAt(Instant.now());
        lastProgressAt(ready, Instant.now().minus(Duration.ofHours(1)));

        int failed = sweepDownloadJobsService.failStale(Instant.now());

        assertThat(failed).isZero();
        assertThat(downloadJobRepository.findById(ready.getId()).orElseThrow().getStatus())
            .isEqualTo(ReadyDownloadJobStatus.INSTANCE);
    }

    // updated_at 은 저장할 때 덮어써지므로 행을 직접 고친다.
    private void lastProgressAt(DownloadJob job, Instant updatedAt) {
        jdbcTemplate.update("UPDATE download_job SET updated_at = ? WHERE id = ?",
            Timestamp.from(updatedAt), job.getId());
    }
}
