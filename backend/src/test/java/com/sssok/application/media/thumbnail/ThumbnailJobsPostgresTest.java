package com.sssok.application.media.thumbnail;

import static com.sssok.support.UploadSizePolicyFixture.SIZE_POLICY;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.sssok.application.media.MediaRegistrar;
import com.sssok.application.port.out.FileRepository;
import com.sssok.application.port.out.FileStoragePort;
import com.sssok.application.port.out.OrphanObjectRepository;
import com.sssok.common.monitoring.MediaProcessingMetrics.Kind;
import com.sssok.domain.file.FileSize;
import com.sssok.domain.file.ProcessedMedia;
import com.sssok.domain.file.StoredFile;
import com.sssok.domain.file.UploadStatus;
import com.sssok.infrastructure.persistence.thumbnail.ThumbnailJob;
import com.sssok.infrastructure.persistence.thumbnail.ThumbnailJob.State;
import com.sssok.infrastructure.persistence.thumbnail.ThumbnailJobRepository;
import com.sssok.support.PostgresContainerSupport;
import com.sssok.support.PostgresIntegrationTest;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@PostgresIntegrationTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "spring.datasource.driver-class-name=org.postgresql.Driver",
    "spring.jpa.hibernate.ddl-auto=validate", "spring.flyway.enabled=true",
    "media.thumbnail.queue.max-attempts=2"
})
class ThumbnailJobsPostgresTest extends PostgresContainerSupport {
    @Autowired ThumbnailJobs jobs;
    @Autowired ThumbnailJobRepository repository;
    @Autowired FileRepository files;
    @Autowired MediaRegistrar registrar;
    @Autowired OrphanObjectRepository orphans;
    @Autowired PlatformTransactionManager transactions;
    @Autowired javax.sql.DataSource dataSource;
    @MockitoBean(name = "thumbnailClock") Clock clock;
    @MockitoBean FileStoragePort storage;
    final AtomicReference<Instant> now = new AtomicReference<>();

    @BeforeEach
    void setup() {
        ((com.zaxxer.hikari.HikariDataSource) dataSource).setMaximumPoolSize(6);
        repository.deleteAll();
        now.set(Instant.parse("2030-01-01T00:00:00Z"));
        when(clock.instant()).thenAnswer(i -> now.get());
    }

    @Test
    void 등록과_작업기록은_함께_커밋되거나_롤백된다() {
        StoredFile file = reserve();
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            registrar.register(file.getRoomId(), List.of(file), "업로더");
            assertThat(repository.findById(file.getId())).isPresent();
            tx.setRollbackOnly();
        });
        assertThat(repository.findById(file.getId())).isEmpty();
        assertThat(files.findById(file.getId()).orElseThrow().getStatus()).isEqualTo(UploadStatus.RESERVED);
        registrar.register(file.getRoomId(), List.of(file), "업로더");
        assertThat(repository.findById(file.getId()).orElseThrow().getState()).isEqualTo(State.PENDING);
        assertThat(files.findById(file.getId()).orElseThrow().getStatus()).isEqualTo(UploadStatus.PROCESSING);
    }

    @Test
    void 잠긴_작업을_기다리지_않고_다음_작업을_선점한다() throws Exception {
        StoredFile first = pending();
        StoredFile second = pending();
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var holder = pool.submit(() -> new TransactionTemplate(transactions).execute(tx -> {
                ThumbnailJob job = repository.claimable("IMAGE", now.get()).orElseThrow();
                job.claim(now.get(), now.get().plusSeconds(120));
                locked.countDown();
                try { assertThat(release.await(10, TimeUnit.SECONDS)).isTrue(); }
                catch (InterruptedException e) { throw new RuntimeException(e); }
                return job.getMediaId();
            }));
            try {
                assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();
                var other = pool.submit(() -> jobs.claim(Kind.IMAGE).orElseThrow());
                assertThat(other.get(3, TimeUnit.SECONDS).mediaId()).isEqualTo(second.getId());
            } finally { release.countDown(); }
            assertThat(holder.get(5, TimeUnit.SECONDS)).isEqualTo(first.getId());
        }
        assertThat(jobs.claim(Kind.IMAGE)).isEmpty();
    }

    @Test
    void 제출거절은_시도횟수를_소비하지_않고_즉시_다시_선점가능하다() {
        pending();
        ThumbnailClaim first = jobs.claim(Kind.IMAGE).orElseThrow();
        jobs.rejected(first);
        ThumbnailClaim second = jobs.claim(Kind.IMAGE).orElseThrow();
        assertThat(second.attempt()).isEqualTo(1);
        assertThat(second.token()).isNotEqualTo(first.token());
        jobs.rejected(first);
        assertThat(repository.findById(second.mediaId()).orElseThrow().getState()).isEqualTo(State.RUNNING);
    }

    @Test
    void 백오프_전에는_재선점하지_않고_한도소진시_최종실패한다() {
        StoredFile file = pending();
        ThumbnailClaim first = jobs.claim(Kind.IMAGE).orElseThrow();
        jobs.failed(first, false, "NETWORK");
        ThumbnailJob retry = repository.findById(file.getId()).orElseThrow();
        assertThat(retry.getState()).isEqualTo(State.RETRY_WAIT);
        assertThat(retry.getAvailableAt()).isBetween(now.get().plusMillis(2500), now.get().plusSeconds(5));
        assertThat(jobs.claim(Kind.IMAGE)).isEmpty();
        now.set(retry.getAvailableAt());
        jobs.failed(jobs.claim(Kind.IMAGE).orElseThrow(), false, "NETWORK");
        assertThat(repository.findById(file.getId()).orElseThrow().getState()).isEqualTo(State.FAILED);
        assertThat(files.findById(file.getId()).orElseThrow().getStatus()).isEqualTo(UploadStatus.FAILED);
        assertThat(jobs.claim(Kind.IMAGE)).isEmpty();
    }

    @Test
    void heartbeat가_살아있으면_회수하지_않고_재시작후_만료된_작업만_복구한다() {
        StoredFile file = pending();
        ThumbnailClaim claim = jobs.claim(Kind.IMAGE).orElseThrow();
        now.set(now.get().plusSeconds(90));
        assertThat(jobs.heartbeat(claim)).isEqualTo(ThumbnailJobs.Heartbeat.RENEWED);
        now.set(now.get().plusSeconds(40));
        jobs.recover(file.getId());
        assertThat(repository.findById(file.getId()).orElseThrow().getState()).isEqualTo(State.RUNNING);
        now.set(now.get().plusSeconds(100));
        assertThat(jobs.expired()).contains(file.getId());
        assertThat(jobs.heartbeat(claim)).isEqualTo(ThumbnailJobs.Heartbeat.LOST);
        jobs.recover(file.getId());
        assertThat(repository.findById(file.getId()).orElseThrow().getState()).isEqualTo(State.RETRY_WAIT);
    }

    @Test
    void 만료된_워커의_늦은_완료는_새_결과를_덮거나_지우지_않는다() {
        StoredFile file = pending();
        ThumbnailClaim old = jobs.claim(Kind.IMAGE).orElseThrow();
        var oldKey = file.getStorageKey().attempt(old.token()).thumbnail("webp");
        assertThat(jobs.artifacts(old, oldKey, null)).isTrue();
        now.set(now.get().plusSeconds(121));
        jobs.recover(file.getId());
        now.set(repository.findById(file.getId()).orElseThrow().getAvailableAt());
        ThumbnailClaim fresh = jobs.claim(Kind.IMAGE).orElseThrow();
        var newKey = file.getStorageKey().attempt(fresh.token()).thumbnail("webp");
        var result = ProcessedMedia.ofImage(newKey, null, 100, 100, null, null);
        jobs.finish(fresh, result);
        assertThat(jobs.heartbeat(fresh)).isEqualTo(ThumbnailJobs.Heartbeat.FINISHED);
        jobs.finish(old, ProcessedMedia.ofImage(oldKey, null, 200, 200, null, null));
        jobs.finish(fresh, result); // 완료 응답 유실 후 중복 완료도 채택된 키를 정리하지 않는다.
        jobs.failed(old, true, "LATE_FAILURE");
        assertThat(files.findById(file.getId()).orElseThrow().getThumbnailKey()).isEqualTo(newKey);
        assertThat(repository.findById(file.getId()).orElseThrow().getState()).isEqualTo(State.SUCCEEDED);
        assertThat(orphans.findStale(Instant.now().plusSeconds(3600), 1000))
            .extracting(o -> o.storageKey()).contains(oldKey).doesNotContain(newKey);
    }

    @Test
    void 삭제된_미디어는_취소되고_늦게_생성한_파일은_정리대상이다() {
        StoredFile file = pending();
        ThumbnailClaim claim = jobs.claim(Kind.IMAGE).orElseThrow();
        new TransactionTemplate(transactions).executeWithoutResult(tx ->
            files.deleteAllByIdIn(List.of(file.getId())));
        var key = file.getStorageKey().attempt(claim.token()).thumbnail("webp");
        jobs.finish(claim, ProcessedMedia.ofImage(key, null, 100, 100, null, null));
        assertThat(repository.findById(file.getId()).orElseThrow().getState()).isEqualTo(State.CANCELLED);
        assertThat(files.findById(file.getId())).isEmpty();
    }

    @Test
    void 기존_PROCESSING_보충은_반복해도_작업과_시도횟수를_덮지_않는다() {
        StoredFile file = reserve();
        file.startProcessing();
        files.save(file);
        assertThat(jobs.missing()).contains(file.getId());
        jobs.backfill(file.getId());
        ThumbnailClaim claim = jobs.claim(Kind.IMAGE).orElseThrow();
        jobs.backfill(file.getId());
        assertThat(repository.findById(file.getId()).orElseThrow().getClaimToken()).isEqualTo(claim.token());
    }

    @Test
    void 최대실행시간이_지나면_heartbeat도_임대를_연장하지_않는다() {
        pending();
        ThumbnailClaim claim = jobs.claim(Kind.IMAGE).orElseThrow();
        for (int minute = 1; minute <= 9; minute++) {
            now.set(claim.startedAt().plusSeconds(minute * 60L));
            assertThat(jobs.heartbeat(claim)).isEqualTo(ThumbnailJobs.Heartbeat.RENEWED);
        }
        assertThat(repository.findById(claim.mediaId()).orElseThrow().getLeaseUntil())
            .isEqualTo(claim.startedAt().plusSeconds(600));
        now.set(claim.startedAt().plusSeconds(600));
        assertThat(jobs.heartbeat(claim)).isEqualTo(ThumbnailJobs.Heartbeat.LOST);
        jobs.recover(claim.mediaId());
        assertThat(repository.findById(claim.mediaId()).orElseThrow().getState()).isEqualTo(State.RETRY_WAIT);
    }

    @Test
    void 재업로드로_사용자가_명시적으로_재접수하면_최종실패_작업도_새로_시작한다() {
        StoredFile file = pending();
        jobs.failed(jobs.claim(Kind.IMAGE).orElseThrow(), true, "INVALID_IMAGE");
        StoredFile failed = files.findById(file.getId()).orElseThrow();
        registrar.register(file.getRoomId(), List.of(failed), "업로더");
        ThumbnailClaim restarted = jobs.claim(Kind.IMAGE).orElseThrow();
        assertThat(restarted.attempt()).isEqualTo(1);
    }

    @Autowired ThumbnailDispatcher dispatcher;
    @Autowired org.springframework.context.ApplicationContext context;

    @Test
    void heartbeat와_일반_배치_스케줄러는_서로_분리된다() {
        var scheduler = context.getBean(org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler.class);
        assertThat(scheduler.getScheduledThreadPoolExecutor().getCorePoolSize()).isEqualTo(2);
        assertThat(context.getBeansOfType(java.util.concurrent.ScheduledExecutorService.class)).isEmpty();
    }


    @Test
    void 입력종료후_실제_워커가_대기열을_비우고_완료와_최종실패로_수렴한다() throws Exception {
        // 실제 PNG 변환과 DB 선점/완료를 연결한다. 외부 스토리지·AI 호출은 하지 않는다.
        var out = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(1280, 960,
            java.awt.image.BufferedImage.TYPE_INT_RGB), "png", out);
        byte[] valid = out.toByteArray();
        java.util.Set<com.sssok.domain.file.StorageKey> invalid = new java.util.HashSet<>();
        for (int i = 0; i < 12; i++) {
            StoredFile file = pending();
            if (i % 4 == 0) { invalid.add(file.getStorageKey()); }
        }
        when(storage.openDownloadStream(any())).thenAnswer(call ->
            new java.io.ByteArrayInputStream(invalid.contains(call.getArgument(0)) ? new byte[]{1, 2} : valid));
        when(storage.openUploadStream(any(), any())).thenAnswer(call ->
            new com.sssok.application.port.out.AbortableOutputStream() {
                @Override public void write(int b) {}
                @Override public void abort() {}
            });
        org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(20)).untilAsserted(() -> {
            dispatcher.dispatch();
            assertThat(repository.findAll()).allMatch(j -> j.getState() == State.SUCCEEDED || j.getState() == State.FAILED);
        });
        assertThat(repository.findAll().stream().filter(j -> j.getState() == State.SUCCEEDED).count()).isEqualTo(9);
        assertThat(repository.findAll().stream().filter(j -> j.getState() == State.FAILED).count()).isEqualTo(3);
    }

    private StoredFile pending() {
        StoredFile file = reserve();
        file.startProcessing();
        files.save(file);
        jobs.enqueue(file);
        return file;
    }

    private StoredFile reserve() {
        return files.save(StoredFile.reserve(532L, 532L, "image.png", "image/png",
            new FileSize(1024), Instant.now(), SIZE_POLICY));
    }
}
