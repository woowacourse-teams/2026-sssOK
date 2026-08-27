package com.sssok.application.download;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.sssok.application.port.out.DownloadJobRepository;
import com.sssok.domain.download.DownloadJob;
import com.sssok.domain.download.DownloadJobStatus;
import com.sssok.domain.file.StorageKey;
import com.sssok.infrastructure.config.DownloadProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

// 잡 하나가 실패해도 나머지 정리가 멈추지 않아야 한다.
class SweepDownloadJobsServiceFailureTest {

    private static final Instant NOW = Instant.parse("2026-08-27T00:00:00Z");

    private final DownloadJobRepository downloadJobRepository = mock(DownloadJobRepository.class);
    private final DownloadJobExpirer downloadJobExpirer = mock(DownloadJobExpirer.class);
    private final DownloadProperties downloadProperties =
        new DownloadProperties(Duration.ofMinutes(5), 3, Duration.ofHours(1));

    private final SweepDownloadJobsService sweepDownloadJobsService =
        new SweepDownloadJobsService(downloadJobRepository, downloadJobExpirer, downloadProperties);

    @Test
    void 한_잡이_실패해도_나머지는_계속_정리한다() {
        DownloadJob failing = job(1L);
        DownloadJob healthy = job(2L);
        given(downloadJobRepository.findAllExpiredReady(any())).willReturn(List.of(failing, healthy));
        willThrow(new IllegalStateException("스토리지 오류")).given(downloadJobExpirer).expire(failing);

        int swept = sweepDownloadJobsService.sweepExpired(NOW);

        assertThat(swept).isEqualTo(1);
        verify(downloadJobExpirer, times(1)).expire(healthy);
    }

    @Test
    void 전부_실패해도_예외를_밖으로_던지지_않는다() {
        given(downloadJobRepository.findAllExpiredReady(any())).willReturn(List.of(job(1L)));
        willThrow(new IllegalStateException("스토리지 오류")).given(downloadJobExpirer).expire(any());

        // 배치가 죽으면 다음 회차까지 아무것도 정리되지 않는다.
        assertThat(sweepDownloadJobsService.sweepExpired(NOW)).isZero();
    }

    private DownloadJob job(Long id) {
        return DownloadJob.reconstruct(id, 1L, 100L, DownloadJobStatus.READY, 1, 1024L,
            "sssOK_1.zip", new StorageKey("rooms/1/downloads/" + id + ".zip"), 100,
            NOW.minus(Duration.ofHours(3)), NOW.minus(Duration.ofHours(2)), null);
    }
}
