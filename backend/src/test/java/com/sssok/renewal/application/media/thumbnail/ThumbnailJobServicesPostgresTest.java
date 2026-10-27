package com.sssok.renewal.application.media.thumbnail;

import com.sssok.application.port.out.FileRepository;
import com.sssok.domain.file.FileSize;
import com.sssok.domain.file.ProcessedMedia;
import com.sssok.domain.file.StorageKey;
import com.sssok.domain.file.StoredFile;
import com.sssok.domain.file.UploadStatus;
import com.sssok.infrastructure.realtime.RoomEventJpaRepository;
import com.sssok.renewal.application.media.upload.complete.MediaRegistrationService;
import com.sssok.renewal.domain.media.thumbnail.ThumbnailClaim;
import com.sssok.renewal.domain.media.thumbnail.ThumbnailJob;
import com.sssok.renewal.domain.media.thumbnail.ThumbnailKind;
import com.sssok.renewal.outgoing.database.OrphanObjectRepository;
import com.sssok.renewal.outgoing.database.ThumbnailJobJpaEntity;
import com.sssok.renewal.outgoing.database.ThumbnailJobJpaRepository;
import com.sssok.renewal.outgoing.storage.FileStorage;
import com.sssok.support.PostgresContainerSupport;
import com.sssok.support.PostgresIntegrationTest;
import com.sssok.support.UploadRoomFixtures;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
import javax.sql.DataSource;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static com.sssok.support.UploadSizePolicyFixture.SIZE_POLICY;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;


@PostgresIntegrationTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "spring.datasource.driver-class-name=org.postgresql.Driver",
    "spring.jpa.hibernate.ddl-auto=validate", "spring.flyway.enabled=true",
    "media.thumbnail.queue.max-attempts=2"
})
class ThumbnailJobServicesPostgresTest extends PostgresContainerSupport {
    @Autowired
    private JdbcTemplate roomFixtureJdbc;

    @Autowired ThumbnailJobService thumbnailJobService;
    @Autowired ThumbnailJobCompletionService thumbnailJobCompletionService;
    @Autowired ThumbnailJobJpaRepository repository;
    @Autowired FileRepository files;
    @Autowired MediaRegistrationService mediaRegistrationService;
    @Autowired OrphanObjectRepository orphans;
    @Autowired PlatformTransactionManager transactions;
    @Autowired DataSource dataSource;
    @MockitoBean(name = "thumbnailClock") Clock clock;
    @MockitoBean FileStorage storage;
    final AtomicReference<Instant> now = new AtomicReference<>();

    @BeforeEach
    void setup() {
        UploadRoomFixtures.ensure(roomFixtureJdbc, 532L);

        Mockito.when(storage.presignGet(
            ArgumentMatchers.any(), ArgumentMatchers.anyString(),
            ArgumentMatchers.anyString(), ArgumentMatchers.any()))
            .thenReturn("https://storage.example.com/read");
        ((com.zaxxer.hikari.HikariDataSource) dataSource).setMaximumPoolSize(6);
        repository.deleteAll();
        now.set(Instant.parse("2030-01-01T00:00:00Z"));
        when(clock.instant()).thenAnswer(i -> now.get());
    }

    @Test
    void 등록과_작업기록은_함께_커밋되거나_롤백된다() {
        StoredFile file = reserve();
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            mediaRegistrationService.register(file.getRoomId(), List.of(file), "업로더");
            assertThat(repository.findById(file.getId())).isPresent();
            tx.setRollbackOnly();
        });
        assertThat(repository.findById(file.getId())).isEmpty();
        assertThat(files.findById(file.getId()).orElseThrow().getStatus()).isEqualTo(UploadStatus.RESERVED);
        mediaRegistrationService.register(file.getRoomId(), List.of(file), "업로더");
        assertThat(repository.findById(file.getId()).orElseThrow().getState()).isEqualTo("PENDING");
        assertThat(files.findById(file.getId()).orElseThrow().getStatus()).isEqualTo(UploadStatus.PROCESSING);
    }

    @Test
    void 잠긴_작업을_기다리지_않고_다음_작업을_선점한다() throws Exception {
        StoredFile first = pending();
        StoredFile second = pending();
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<Long> holder = pool.submit(() -> new TransactionTemplate(transactions).execute(tx -> {
                ThumbnailJobJpaEntity row = repository.claimable("IMAGE", now.get()).orElseThrow();
                ThumbnailJob job = row.toDomain();
                job.claim(now.get(), now.get().plusSeconds(120));
                row.apply(job);
                locked.countDown();
                try { assertThat(release.await(10, TimeUnit.SECONDS)).isTrue(); }
                catch (InterruptedException e) { throw new RuntimeException(e); }
                return job.getMediaId();
            }));
            try {
                assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();
                Future<ThumbnailClaim> other = pool.submit(() -> thumbnailJobService.claimNext(ThumbnailKind.IMAGE).orElseThrow());
                assertThat(other.get(3, TimeUnit.SECONDS).mediaId()).isEqualTo(second.getId());
            } finally { release.countDown(); }
            assertThat(holder.get(5, TimeUnit.SECONDS)).isEqualTo(first.getId());
        }
        assertThat(thumbnailJobService.claimNext(ThumbnailKind.IMAGE)).isEmpty();
    }

    @Test
    void 제출거절은_시도횟수를_소비하지_않고_즉시_다시_선점가능하다() {
        pending();
        ThumbnailClaim first = thumbnailJobService.claimNext(ThumbnailKind.IMAGE).orElseThrow();
        thumbnailJobService.returnRejected(first);
        ThumbnailClaim second = thumbnailJobService.claimNext(ThumbnailKind.IMAGE).orElseThrow();
        assertThat(second.attempt()).isEqualTo(1);
        assertThat(second.token()).isNotEqualTo(first.token());
        thumbnailJobService.returnRejected(first);
        assertThat(repository.findById(second.mediaId()).orElseThrow().getState()).isEqualTo("RUNNING");
    }

    @Test
    void 백오프_전에는_재선점하지_않고_한도소진시_최종실패한다() {
        StoredFile file = pending();
        ThumbnailClaim first = thumbnailJobService.claimNext(ThumbnailKind.IMAGE).orElseThrow();
        thumbnailJobCompletionService.fail(first, false, "NETWORK");
        ThumbnailJobJpaEntity retry = repository.findById(file.getId()).orElseThrow();
        assertThat(retry.getState()).isEqualTo("RETRY_WAIT");
        assertThat(retry.getAvailableAt()).isBetween(now.get().plusMillis(2500), now.get().plusSeconds(5));
        assertThat(thumbnailJobService.claimNext(ThumbnailKind.IMAGE)).isEmpty();
        now.set(retry.getAvailableAt());
        thumbnailJobCompletionService.fail(thumbnailJobService.claimNext(ThumbnailKind.IMAGE).orElseThrow(), false, "NETWORK");
        assertThat(repository.findById(file.getId()).orElseThrow().getState()).isEqualTo("FAILED");
        assertThat(files.findById(file.getId()).orElseThrow().getStatus()).isEqualTo(UploadStatus.FAILED);
        assertThat(thumbnailJobService.claimNext(ThumbnailKind.IMAGE)).isEmpty();
    }

    @Test
    void heartbeat가_살아있으면_회수하지_않고_재시작후_만료된_작업만_복구한다() {
        StoredFile file = pending();
        ThumbnailClaim claim = thumbnailJobService.claimNext(ThumbnailKind.IMAGE).orElseThrow();
        now.set(now.get().plusSeconds(90));
        assertThat(thumbnailJobService.renewLease(claim)).isEqualTo(ThumbnailJobService.Heartbeat.RENEWED);
        now.set(now.get().plusSeconds(40));
        thumbnailJobCompletionService.recoverExpired(file.getId());
        assertThat(repository.findById(file.getId()).orElseThrow().getState()).isEqualTo("RUNNING");
        now.set(now.get().plusSeconds(100));
        assertThat(thumbnailJobCompletionService.findExpiredMediaIds()).contains(file.getId());
        assertThat(thumbnailJobService.renewLease(claim)).isEqualTo(ThumbnailJobService.Heartbeat.LOST);
        thumbnailJobCompletionService.recoverExpired(file.getId());
        assertThat(repository.findById(file.getId()).orElseThrow().getState()).isEqualTo("RETRY_WAIT");
    }

    @Test
    void 만료된_워커의_늦은_완료는_새_결과를_덮거나_지우지_않는다() {
        StoredFile file = pending();
        ThumbnailClaim old = thumbnailJobService.claimNext(ThumbnailKind.IMAGE).orElseThrow();
        StorageKey oldKey = file.getStorageKey().attempt(old.token()).thumbnail("webp");
        assertThat(thumbnailJobService.recordGeneratedFiles(old, oldKey, null)).isTrue();
        now.set(now.get().plusSeconds(121));
        thumbnailJobCompletionService.recoverExpired(file.getId());
        now.set(repository.findById(file.getId()).orElseThrow().getAvailableAt());
        ThumbnailClaim fresh = thumbnailJobService.claimNext(ThumbnailKind.IMAGE).orElseThrow();
        StorageKey newKey = file.getStorageKey().attempt(fresh.token()).thumbnail("webp");
        ProcessedMedia result = ProcessedMedia.ofImage(newKey, null, 100, 100, null, null);
        thumbnailJobCompletionService.complete(fresh, result);
        assertThat(thumbnailJobService.renewLease(fresh)).isEqualTo(ThumbnailJobService.Heartbeat.FINISHED);
        thumbnailJobCompletionService.complete(old, ProcessedMedia.ofImage(oldKey, null, 200, 200, null, null));
        thumbnailJobCompletionService.complete(fresh, result); // 완료 응답 유실 후 중복 완료도 채택된 키를 정리하지 않는다.
        thumbnailJobCompletionService.fail(old, true, "LATE_FAILURE");
        assertThat(files.findById(file.getId()).orElseThrow().getThumbnailKey()).isEqualTo(newKey);
        assertThat(repository.findById(file.getId()).orElseThrow().getState()).isEqualTo("SUCCEEDED");
        assertThat(orphans.findStale(Instant.now().plusSeconds(3600), 1000))
            .extracting(o -> o.storageKey()).contains(oldKey).doesNotContain(newKey);
    }

    @Test
    void 삭제된_미디어는_취소되고_늦게_생성한_파일은_정리대상이다() {
        StoredFile file = pending();
        ThumbnailClaim claim = thumbnailJobService.claimNext(ThumbnailKind.IMAGE).orElseThrow();
        new TransactionTemplate(transactions).executeWithoutResult(tx ->
            files.deleteAllByIdIn(List.of(file.getId())));
        StorageKey key = file.getStorageKey().attempt(claim.token()).thumbnail("webp");
        thumbnailJobCompletionService.complete(claim, ProcessedMedia.ofImage(key, null, 100, 100, null, null));
        assertThat(repository.findById(file.getId()).orElseThrow().getState()).isEqualTo("CANCELLED");
        assertThat(files.findById(file.getId())).isEmpty();
    }

    @Test
    void 기존_PROCESSING_보충은_반복해도_작업과_시도횟수를_덮지_않는다() {
        StoredFile file = reserve();
        file.startProcessing();
        files.save(file);
        assertThat(thumbnailJobService.findMissingMediaIds()).contains(file.getId());
        thumbnailJobService.enqueueMissing(file.getId());
        ThumbnailClaim claim = thumbnailJobService.claimNext(ThumbnailKind.IMAGE).orElseThrow();
        thumbnailJobService.enqueueMissing(file.getId());
        assertThat(repository.findById(file.getId()).orElseThrow().getClaimToken()).isEqualTo(claim.token());
    }

    @Test
    void 최대실행시간이_지나면_heartbeat도_임대를_연장하지_않는다() {
        pending();
        ThumbnailClaim claim = thumbnailJobService.claimNext(ThumbnailKind.IMAGE).orElseThrow();
        for (int minute = 1; minute <= 9; minute++) {
            now.set(claim.startedAt().plusSeconds(minute * 60L));
            assertThat(thumbnailJobService.renewLease(claim)).isEqualTo(ThumbnailJobService.Heartbeat.RENEWED);
        }
        assertThat(repository.findById(claim.mediaId()).orElseThrow().getLeaseUntil())
            .isEqualTo(claim.startedAt().plusSeconds(600));
        now.set(claim.startedAt().plusSeconds(600));
        assertThat(thumbnailJobService.renewLease(claim)).isEqualTo(ThumbnailJobService.Heartbeat.LOST);
        thumbnailJobCompletionService.recoverExpired(claim.mediaId());
        assertThat(repository.findById(claim.mediaId()).orElseThrow().getState()).isEqualTo("RETRY_WAIT");
    }

    @Test
    void 재업로드로_사용자가_명시적으로_재접수하면_최종실패_작업도_새로_시작한다() {
        StoredFile file = pending();
        thumbnailJobCompletionService.fail(thumbnailJobService.claimNext(ThumbnailKind.IMAGE).orElseThrow(), true, "INVALID_IMAGE");
        StoredFile failed = files.findById(file.getId()).orElseThrow();
        mediaRegistrationService.register(file.getRoomId(), List.of(failed), "업로더");
        ThumbnailClaim restarted = thumbnailJobService.claimNext(ThumbnailKind.IMAGE).orElseThrow();
        assertThat(restarted.attempt()).isEqualTo(1);
    }

    @Autowired ThumbnailDispatcher dispatcher;
    @Autowired ApplicationContext context;

    @Test
    void heartbeat와_일반_배치_스케줄러는_서로_분리된다() {
        ThreadPoolTaskScheduler scheduler = context.getBean(ThreadPoolTaskScheduler.class);
        assertThat(scheduler.getScheduledThreadPoolExecutor().getCorePoolSize()).isEqualTo(2);
        assertThat(context.getBeansOfType(ScheduledExecutorService.class)).isEmpty();
    }


    @Test
    void 입력종료후_실제_워커가_대기열을_비우고_완료와_최종실패로_수렴한다() throws Exception {
        // 실제 PNG 변환과 DB 선점/완료를 연결한다. 외부 스토리지·AI 호출은 하지 않는다.
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(1280, 960,
            BufferedImage.TYPE_INT_RGB), "png", out);
        byte[] valid = out.toByteArray();
        Set<StorageKey> invalid = new HashSet<>();
        for (int i = 0; i < 12; i++) {
            StoredFile file = pending();
            if (i % 4 == 0) { invalid.add(file.getStorageKey()); }
        }
        when(storage.openDownloadStream(any())).thenAnswer(call ->
            new ByteArrayInputStream(invalid.contains(call.getArgument(0)) ? new byte[]{1, 2} : valid));

        Awaitility.await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            dispatcher.dispatchPendingJobs();
            assertThat(repository.findAll()).allMatch(j -> "SUCCEEDED".equals(j.getState()) || "FAILED".equals(j.getState()));
        });
        assertThat(repository.findAll().stream().filter(j -> "SUCCEEDED".equals(j.getState())).count()).isEqualTo(9);
        assertThat(repository.findAll().stream().filter(j -> "FAILED".equals(j.getState())).count()).isEqualTo(3);
    }


    @Autowired RoomEventJpaRepository roomEvents;

    @Test
    void 완료_트랜잭션_롤백은_READY와_작업성공과_이벤트를_모두_취소한다() {
        StoredFile file = pending();
        ThumbnailClaim claim = thumbnailJobService.claimNext(ThumbnailKind.IMAGE).orElseThrow();
        long eventCount = readyEventCount();
        new TransactionTemplate(transactions).executeWithoutResult(transaction -> {
            assertThat(thumbnailJobCompletionService.complete(claim, ProcessedMedia.none())).isTrue();
            assertThat(repository.findById(file.getId()).orElseThrow().getState()).isEqualTo("SUCCEEDED");
            transaction.setRollbackOnly();
        });
        assertThat(repository.findById(file.getId()).orElseThrow().getState()).isEqualTo("RUNNING");
        assertThat(files.findById(file.getId()).orElseThrow().getStatus()).isEqualTo(UploadStatus.PROCESSING);
        assertThat(readyEventCount()).isEqualTo(eventCount);
        assertThat(thumbnailJobCompletionService.complete(claim, ProcessedMedia.none())).isTrue();
        assertThat(readyEventCount()).isEqualTo(eventCount + 1);
    }

    @Test
    void 영구실패_롤백은_파일과_작업상태를_함께_유지한다() {
        StoredFile file = pending();
        ThumbnailClaim claim = thumbnailJobService.claimNext(ThumbnailKind.IMAGE).orElseThrow();
        new TransactionTemplate(transactions).executeWithoutResult(transaction -> {
            thumbnailJobCompletionService.fail(claim, true, "INVALID_IMAGE");
            transaction.setRollbackOnly();
        });
        assertThat(repository.findById(file.getId()).orElseThrow().getState()).isEqualTo("RUNNING");
        assertThat(files.findById(file.getId()).orElseThrow().getStatus()).isEqualTo(UploadStatus.PROCESSING);
    }

    @Test
    void 임대_만료_경계에서_복구는_한번만_적용하고_이전토큰의_파일기록을_거절한다() {
        StoredFile file = pending();
        ThumbnailClaim expiredClaim = thumbnailJobService.claimNext(ThumbnailKind.IMAGE).orElseThrow();
        now.set(repository.findById(file.getId()).orElseThrow().getLeaseUntil());
        assertThat(thumbnailJobService.renewLease(expiredClaim)).isEqualTo(ThumbnailJobService.Heartbeat.LOST);
        thumbnailJobCompletionService.recoverExpired(file.getId());
        Instant retryAt = repository.findById(file.getId()).orElseThrow().getAvailableAt();
        thumbnailJobCompletionService.recoverExpired(file.getId());
        assertThat(repository.findById(file.getId()).orElseThrow().getAvailableAt()).isEqualTo(retryAt);
        now.set(retryAt);
        ThumbnailClaim freshClaim = thumbnailJobService.claimNext(ThumbnailKind.IMAGE).orElseThrow();
        StorageKey lateKey = file.getStorageKey().attempt(expiredClaim.token()).thumbnail("webp");
        assertThat(thumbnailJobService.recordGeneratedFiles(expiredClaim, lateKey, null)).isFalse();
        assertThat(repository.findById(file.getId()).orElseThrow().getClaimToken()).isEqualTo(freshClaim.token());
        assertThat(repository.findById(file.getId()).orElseThrow().getThumbnailKey()).isNull();
    }

    @Test
    void 복구_롤백은_재시도예약과_고아파일_정리등록을_취소한다() {
        StoredFile file = pending();
        ThumbnailClaim claim = thumbnailJobService.claimNext(ThumbnailKind.IMAGE).orElseThrow();
        StorageKey generatedKey = file.getStorageKey().attempt(claim.token()).thumbnail("webp");
        thumbnailJobService.recordGeneratedFiles(claim, generatedKey, null);
        now.set(now.get().plusSeconds(120));
        new TransactionTemplate(transactions).executeWithoutResult(transaction -> {
            thumbnailJobCompletionService.recoverExpired(file.getId());
            transaction.setRollbackOnly();
        });
        assertThat(repository.findById(file.getId()).orElseThrow().getState()).isEqualTo("RUNNING");
        assertThat(orphans.findStale(Instant.now().plusSeconds(3600), 10000))
            .extracting(orphan -> orphan.storageKey()).doesNotContain(generatedKey);
    }

    @Test
    void 완료는_파일잠금을_기다린_뒤_최신상태를_확인한다() throws Exception {
        StoredFile file = pending();
        ThumbnailClaim claim = thumbnailJobService.claimNext(ThumbnailKind.IMAGE).orElseThrow();
        AtomicReference<Future<Boolean>> completion = new AtomicReference<>();
        CountDownLatch started = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            new TransactionTemplate(transactions).executeWithoutResult(transaction -> {
                StoredFile lockedFile = files.findByIdForUpdate(file.getId()).orElseThrow();
                completion.set(executor.submit(() -> {
                    started.countDown();
                    return thumbnailJobCompletionService.complete(claim, ProcessedMedia.none());
                }));
                try { assertThat(started.await(5, TimeUnit.SECONDS)).isTrue(); }
                catch (InterruptedException exception) { throw new RuntimeException(exception); }
                assertThatThrownBy(() -> completion.get().get(200, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);
                // 완료가 파일 잠금을 기다리는 동안 작업 행은 먼저 잠그지 않아야 한다.
                roomFixtureJdbc.queryForObject("select media_id from thumbnail_job where media_id = ? for update nowait",
                    Long.class, file.getId());
                lockedFile.completeProcessing(ProcessedMedia.none());
                files.save(lockedFile);
            });
            assertThat(completion.get().get(5, TimeUnit.SECONDS)).isFalse();
        }
        assertThat(repository.findById(file.getId()).orElseThrow().getState()).isEqualTo("SUCCEEDED");
        assertThat(files.findById(file.getId()).orElseThrow().getStatus()).isEqualTo(UploadStatus.READY);
    }

    private long readyEventCount() {
        return roomEvents.findByRoomIdAndIdGreaterThanOrderById(532L, 0L).stream()
            .filter(event -> event.getEventType().equals("media.ready")).count();
    }

    private StoredFile pending() {
        StoredFile file = reserve();
        file.startProcessing();
        files.save(file);
        thumbnailJobService.enqueue(file);
        return file;
    }

    private StoredFile reserve() {
        return files.save(StoredFile.reserve(532L, 532L, "image.png", "image/png",
            new FileSize(1024), Instant.now(), SIZE_POLICY));
    }
}
