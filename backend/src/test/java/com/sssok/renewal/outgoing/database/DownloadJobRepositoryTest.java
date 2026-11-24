package com.sssok.renewal.outgoing.database;

import static org.assertj.core.api.Assertions.assertThat;

import com.sssok.renewal.domain.download.DownloadJob;
import com.sssok.support.PostgresContainerSupport;
import com.sssok.support.PostgresIntegrationTest;
import java.time.Instant;
import java.util.List;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

// 대상 저장은 JPA 를 거치지 않는 JDBC 배치라, 엔티티가 채워 주던 생성·수정 시각까지 직접 확인한다.
@PostgresIntegrationTest
@Transactional
class DownloadJobRepositoryTest extends PostgresContainerSupport {
    private static final int MAX_TARGETS = 1000;

    @Autowired
    private DownloadJobRepository downloadJobRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private Long job() {
        return downloadJobRepository.save(
            DownloadJob.create(1L, 1L, 1, 1L, "sssOK_1.zip", Instant.now())).getId();
    }

    @Test
    void 대상_상한만큼의_미디어를_한_번에_고정한다() {
        Long jobId = job();
        List<Long> mediaIds = LongStream.rangeClosed(1, MAX_TARGETS).boxed().toList();

        downloadJobRepository.saveJobMedia(jobId, mediaIds);

        assertThat(downloadJobRepository.findMediaIdsByJobId(jobId))
            .containsExactlyInAnyOrderElementsOf(mediaIds);
    }

    @Test
    void 고정한_대상에_생성_수정_시각을_남긴다() {
        Long jobId = job();
        Instant before = Instant.now();

        downloadJobRepository.saveJobMedia(jobId, List.of(10L, 20L));

        Instant after = Instant.now();
        List<Instant[]> times = jdbcTemplate.query(
            "select created_at, updated_at from download_job_media where download_job_id = ?",
            (rs, rowNum) -> new Instant[] {
                rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant()},
            jobId);
        assertThat(times).hasSize(2).allSatisfy(pair -> {
            assertThat(pair[0]).isBetween(before.minusMillis(1), after.plusMillis(1));
            assertThat(pair[1]).isEqualTo(pair[0]);
        });
    }

    @Test
    void 다른_잡의_대상과_섞이지_않는다() {
        Long firstJobId = job();
        Long secondJobId = job();

        downloadJobRepository.saveJobMedia(firstJobId, List.of(1L, 2L));
        downloadJobRepository.saveJobMedia(secondJobId, List.of(3L));

        assertThat(downloadJobRepository.findMediaIdsByJobId(firstJobId)).containsExactlyInAnyOrder(1L, 2L);
        assertThat(downloadJobRepository.findMediaIdsByJobId(secondJobId)).containsExactly(3L);
    }

    // 429 판단과 잡·대상 저장이 한 트랜잭션이어야 한다. JDBC 배치가 JPA 트랜잭션의 연결을 그대로 쓰는지 본다.
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void 접수_트랜잭션이_롤백되면_잡과_대상이_함께_사라진다() {
        Long[] jobId = new Long[1];

        transactionTemplate.executeWithoutResult(status -> {
            jobId[0] = job();
            downloadJobRepository.saveJobMedia(jobId[0], List.of(1L, 2L, 3L));
            status.setRollbackOnly();
        });

        assertThat(jdbcTemplate.queryForObject(
            "select count(*) from download_job where id = ?", Long.class, jobId[0])).isZero();
        assertThat(jdbcTemplate.queryForObject(
            "select count(*) from download_job_media where download_job_id = ?", Long.class, jobId[0])).isZero();
    }
}
