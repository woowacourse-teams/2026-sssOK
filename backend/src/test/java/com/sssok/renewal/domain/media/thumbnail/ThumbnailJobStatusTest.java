package com.sssok.renewal.domain.media.thumbnail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sssok.renewal.domain.media.thumbnail.status.CancelledThumbnailJobStatus;
import com.sssok.renewal.domain.media.thumbnail.status.FailedThumbnailJobStatus;
import com.sssok.renewal.domain.media.thumbnail.status.PendingThumbnailJobStatus;
import com.sssok.renewal.domain.media.thumbnail.status.RetryWaitThumbnailJobStatus;
import com.sssok.renewal.domain.media.thumbnail.status.RunningThumbnailJobStatus;
import com.sssok.renewal.domain.media.thumbnail.status.SucceededThumbnailJobStatus;
import com.sssok.renewal.domain.media.thumbnail.status.ThumbnailJobStatus;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ThumbnailJobStatusTest {
    private static final Instant NOW = Instant.parse("2030-01-01T00:00:00Z");

    @Test
    void 저장된_상태를_복원해도_상태별_동작을_유지한다() {
        for (ThumbnailJobStatus status : ThumbnailJobStatus.all()) {
            assertThat(ThumbnailJobStatus.from(status.name())).isSameAs(status);
        }
        assertThatThrownBy(() -> ThumbnailJobStatus.from("UNKNOWN"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 선점_반납_재시도_완료_전이가_기존_규칙을_따른다() {
        ThumbnailJob job = new ThumbnailJob(1L, ThumbnailKind.IMAGE, NOW);
        assertThat(job.getStatus()).isSameAs(PendingThumbnailJobStatus.INSTANCE);
        job.claim(NOW, NOW.plusSeconds(120));
        assertThat(job.getStatus()).isSameAs(RunningThumbnailJobStatus.INSTANCE);
        job.rejected(NOW);
        assertThat(job.getStatus()).isSameAs(PendingThumbnailJobStatus.INSTANCE);
        assertThat(job.getAttempts()).isZero();

        job.claim(NOW, NOW.plusSeconds(120));
        job.retry(NOW.plusSeconds(5), "NETWORK");
        assertThat(job.getStatus()).isSameAs(RetryWaitThumbnailJobStatus.INSTANCE);
        job.claim(NOW.plusSeconds(5), NOW.plusSeconds(125));
        job.rejected(NOW.plusSeconds(5));
        assertThat(job.getStatus()).isSameAs(RetryWaitThumbnailJobStatus.INSTANCE);
        job.claim(NOW.plusSeconds(5), NOW.plusSeconds(125));
        job.complete(SucceededThumbnailJobStatus.INSTANCE, NOW.plusSeconds(10), null);
        assertThat(job.getStatus()).isSameAs(SucceededThumbnailJobStatus.INSTANCE);
    }

    @Test
    void 종료_상태에서는_다시_선점하거나_재시도하지_않는다() {
        for (ThumbnailJobStatus status : java.util.List.of(SucceededThumbnailJobStatus.INSTANCE,
            FailedThumbnailJobStatus.INSTANCE, CancelledThumbnailJobStatus.INSTANCE)) {
            assertThatThrownBy(status::claim).isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(status::retry).isInstanceOf(IllegalStateException.class);
        }
        assertThatThrownBy(() -> PendingThumbnailJobStatus.INSTANCE.complete(SucceededThumbnailJobStatus.INSTANCE))
            .isInstanceOf(IllegalStateException.class);
    }
}
