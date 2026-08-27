package com.sssok.application.download;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sssok.application.download.exception.DownloadRateLimitedException;
import com.sssok.application.media.exception.MediaNotFoundException;
import com.sssok.domain.download.DownloadJobStatus;
import com.sssok.infrastructure.config.DownloadProperties;
import com.sssok.support.PostgresContainerSupport;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class CreateDownloadJobServiceTest extends PostgresContainerSupport {

    @Autowired
    CreateDownloadJobService createDownloadJobService;

    @Autowired
    DownloadProperties downloadProperties;

    @Autowired
    JdbcTemplate jdbcTemplate;

    private void media(long id, long roomId, String status) {
        jdbcTemplate.update("""
            INSERT INTO stored_file
                (id, room_id, uploader_id, original_file_name, media_type, file_size_bytes,
                 storage_key, status, created_at, updated_at, reserved_at, retry_count)
            VALUES (?, ?, 1, 'test.jpg', 'JPEG', 1024, ?, ?, now(), now(), now(), 0)
            """, id, roomId, "test-key-" + id, status);
    }

    private List<Long> jobMediaIds(Long jobId) {
        return jdbcTemplate.queryForList(
            "select media_id from download_job_media where download_job_id = ?", Long.class, jobId);
    }

    @Test
    void QUEUED_상태의_잡을_만들고_대상을_함께_저장한다() {
        media(1L, 1L, "READY");
        media(2L, 1L, "READY");

        CreateDownloadJobResult result = createDownloadJobService.create(1L, 100L, List.of(1L, 2L), null);

        assertThat(result.status()).isEqualTo(DownloadJobStatus.QUEUED);
        assertThat(result.mediaCount()).isEqualTo(2);
        assertThat(result.totalSize()).isEqualTo(2048L);
        assertThat(result.fileName()).isEqualTo("sssOK_1.zip");
        assertThat(jobMediaIds(result.jobId())).containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    void 대상이_없으면_리졸버의_예외가_그대로_전파된다() {
        assertThatThrownBy(() -> createDownloadJobService.create(1L, 100L, List.of(999L), null))
            .isInstanceOf(MediaNotFoundException.class);
    }

    @Test
    void 동시_진행_중인_잡이_상한에_도달하면_429() {
        media(1L, 1L, "READY");
        int maxJobs = downloadProperties.maxConcurrentJobsPerRequester();
        for (int i = 0; i < maxJobs; i++) {
            createDownloadJobService.create(1L, 100L, List.of(1L), null);
        }

        assertThatThrownBy(() -> createDownloadJobService.create(1L, 100L, List.of(1L), null))
            .isInstanceOf(DownloadRateLimitedException.class);
    }

    @Test
    void 다른_요청자의_진행_중인_잡은_카운트에_영향을_주지_않는다() {
        media(1L, 1L, "READY");
        int maxJobs = downloadProperties.maxConcurrentJobsPerRequester();
        for (int i = 0; i < maxJobs; i++) {
            createDownloadJobService.create(1L, 100L, List.of(1L), null);
        }

        CreateDownloadJobResult result = createDownloadJobService.create(1L, 200L, List.of(1L), null);

        assertThat(result.status()).isEqualTo(DownloadJobStatus.QUEUED);
    }
}
