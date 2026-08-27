package com.sssok.domain.download;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sssok.domain.download.exception.IllegalDownloadJobStatusException;
import com.sssok.domain.file.StorageKey;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class DownloadJobTest {

    private static final Instant NOW = Instant.parse("2026-08-27T10:00:00Z");
    private static final Long ROOM_ID = 1L;
    private static final Long REQUESTER_ID = 100L;

    private static DownloadJob create() {
        return DownloadJob.create(ROOM_ID, REQUESTER_ID, 3, 1_000L, "sssOK_1.zip", NOW);
    }

    @Nested
    class 생성 {

        @Test
        void QUEUED_상태로_시작하고_진행률은_0이다() {
            DownloadJob job = create();

            assertThat(job.getStatus()).isEqualTo(DownloadJobStatus.QUEUED);
            assertThat(job.getProgress()).isEqualTo(0);
            assertThat(job.getZipStorageKey()).isNull();
            assertThat(job.getReadyAt()).isNull();
        }
    }

    @Nested
    class 상태_전이 {

        @Test
        void QUEUED에서_RUNNING으로_전이한다() {
            DownloadJob job = create();

            job.markRunning();

            assertThat(job.getStatus()).isEqualTo(DownloadJobStatus.RUNNING);
        }

        @Test
        void RUNNING에서_READY로_전이하면_zip키와_readyAt과_진행률이_채워진다() {
            DownloadJob job = create();
            job.markRunning();
            StorageKey zipKey = new StorageKey("rooms/1/downloads/job-1.zip");

            job.markReady(zipKey, NOW.plusSeconds(30));

            assertThat(job.getStatus()).isEqualTo(DownloadJobStatus.READY);
            assertThat(job.getZipStorageKey()).isEqualTo(zipKey);
            assertThat(job.getReadyAt()).isEqualTo(NOW.plusSeconds(30));
            assertThat(job.getProgress()).isEqualTo(100);
        }

        @Test
        void RUNNING에서_FAILED로_전이하면_사유가_채워진다() {
            DownloadJob job = create();
            job.markRunning();

            job.markFailed("스토리지 업로드 실패");

            assertThat(job.getStatus()).isEqualTo(DownloadJobStatus.FAILED);
            assertThat(job.getFailureReason()).isEqualTo("스토리지 업로드 실패");
        }

        @Test
        void READY에서_EXPIRED로_전이한다() {
            DownloadJob job = create();
            job.markRunning();
            job.markReady(new StorageKey("rooms/1/downloads/job-1.zip"), NOW);

            job.markExpired();

            assertThat(job.getStatus()).isEqualTo(DownloadJobStatus.EXPIRED);
        }

        @Test
        void QUEUED에서_바로_READY로는_전이할_수_없다() {
            DownloadJob job = create();

            assertThatThrownBy(() -> job.markReady(new StorageKey("rooms/1/downloads/job-1.zip"), NOW))
                .isInstanceOf(IllegalDownloadJobStatusException.class);
        }

        @Test
        void FAILED에서는_어디로도_전이할_수_없다() {
            DownloadJob job = create();
            job.markRunning();
            job.markFailed("실패");

            assertThatThrownBy(job::markRunning).isInstanceOf(IllegalDownloadJobStatusException.class);
        }
    }

    @Nested
    class 본인_확인 {

        @Test
        void 요청자_본인이면_참이다() {
            assertThat(create().isRequestedBy(REQUESTER_ID)).isTrue();
        }

        @Test
        void 다른_사람이면_거짓이다() {
            assertThat(create().isRequestedBy(999L)).isFalse();
        }
    }

    @Nested
    class 만료_여부 {

        @Test
        void READY가_아니면_만료가_아니다() {
            DownloadJob job = create();

            assertThat(job.isExpired(Duration.ofHours(1), NOW.plus(Duration.ofDays(1)))).isFalse();
        }

        @Test
        void 보관_기간이_지나지_않으면_만료가_아니다() {
            DownloadJob job = create();
            job.markRunning();
            job.markReady(new StorageKey("rooms/1/downloads/job-1.zip"), NOW);

            assertThat(job.isExpired(Duration.ofHours(1), NOW.plusSeconds(59 * 60))).isFalse();
        }

        @Test
        void 보관_기간이_지나면_만료다() {
            DownloadJob job = create();
            job.markRunning();
            job.markReady(new StorageKey("rooms/1/downloads/job-1.zip"), NOW);

            assertThat(job.isExpired(Duration.ofHours(1), NOW.plus(Duration.ofHours(2)))).isTrue();
        }
    }
}
