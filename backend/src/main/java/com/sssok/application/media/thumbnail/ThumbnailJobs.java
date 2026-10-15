package com.sssok.application.media.thumbnail;

import com.sssok.application.media.MediaFinisher;
import com.sssok.application.port.out.FileRepository;
import com.sssok.application.storage.OrphanObjectCollector;
import com.sssok.common.monitoring.MediaProcessingMetrics.Kind;
import com.sssok.domain.file.ProcessedMedia;
import com.sssok.domain.file.StorageKey;
import com.sssok.domain.file.StoredFile;
import com.sssok.domain.file.UploadStatus;
import com.sssok.infrastructure.config.ThumbnailQueueProperties;
import com.sssok.infrastructure.persistence.thumbnail.ThumbnailJob;
import com.sssok.infrastructure.persistence.thumbnail.ThumbnailJob.State;
import com.sssok.infrastructure.persistence.thumbnail.ThumbnailJobRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// DB 작업은 짧게 끝낸다. 파일/작업을 함께 잠글 때는 항상 파일 → 작업 순서다.
@Slf4j
@Service
public class ThumbnailJobs {
    private final ThumbnailJobRepository jobs;
    private final FileRepository files;
    private final MediaFinisher finisher;
    private final OrphanObjectCollector orphans;
    private final ThumbnailQueueProperties properties;
    private final Clock clock;
    private final MeterRegistry metrics;

    public ThumbnailJobs(ThumbnailJobRepository jobs, FileRepository files, MediaFinisher finisher,
                         OrphanObjectCollector orphans, ThumbnailQueueProperties properties,
                         @Qualifier("thumbnailClock") Clock clock, MeterRegistry metrics) {
        this.jobs = jobs;
        this.files = files;
        this.finisher = finisher;
        this.orphans = orphans;
        this.properties = properties;
        this.clock = clock;
        this.metrics = metrics;
        for (Kind kind : Kind.values()) {
            io.micrometer.core.instrument.Timer.builder("sssok.thumbnail.ready.latency")
                .tag("kind", kind.name().toLowerCase(java.util.Locale.ROOT))
                .serviceLevelObjectives(Duration.ofSeconds(1), Duration.ofSeconds(5), Duration.ofSeconds(10),
                    Duration.ofSeconds(30), Duration.ofMinutes(1), Duration.ofMinutes(2),
                    Duration.ofMinutes(5), Duration.ofMinutes(10), Duration.ofMinutes(30))
                .register(metrics);
        }
        for (String name : List.of("retried", "failed", "recovered")) {
            metrics.counter("sssok.thumbnail." + name);
        }
    }

    // MediaRegistrar가 파일 행을 잠근 상태에서 호출한다. 기존 작업의 재시도 이력은 덮어쓰지 않는다.
    @Transactional
    public void enqueue(StoredFile file) {
        if (jobs.findById(file.getId()).map(j -> j.getState() == State.FAILED).orElse(true)) {
            jobs.save(new ThumbnailJob(file.getId(), Kind.of(file.getMediaType().isVideo()), clock.instant()));
        }
    }

    @Transactional
    public Optional<ThumbnailClaim> claim(Kind kind) {
        Instant now = clock.instant();
        return jobs.claimable(kind.name(), now).map(job -> {
            job.claim(now, now.plus(properties.lease()));
            log.info("썸네일 작업 선점 mediaId={} claimToken={} attempt={}",
                job.getMediaId(), job.getClaimToken(), job.getAttempts());
            return new ThumbnailClaim(job.getMediaId(), kind, job.getClaimToken(), job.getAttempts(), now);
        });
    }

    @Transactional
    public void rejected(ThumbnailClaim claim) {
        jobs.lock(claim.mediaId()).filter(j -> j.owns(claim.token(), clock.instant()))
            .ifPresent(j -> j.rejected(clock.instant()));
    }

    public enum Heartbeat { RENEWED, LOST, FINISHED }

    @Transactional
    public Heartbeat heartbeat(ThumbnailClaim claim) {
        Instant now = clock.instant();
        Instant deadline = claim.startedAt().plus(properties.maxRuntime());
        return jobs.lock(claim.mediaId()).map(j -> {
            // 완료 커밋 뒤 SSE 후처리 중일 수도 있다. 정상 종료한 워커를 interrupt하지 않는다.
            if (j.getState() != State.RUNNING) { return Heartbeat.FINISHED; }
            if (!j.owns(claim.token(), now) || !now.isBefore(deadline)) { return Heartbeat.LOST; }
            Instant until = now.plus(properties.lease());
            j.heartbeat(until.isAfter(deadline) ? deadline : until);
            return Heartbeat.RENEWED;
        }).orElse(Heartbeat.LOST);
    }

    @Transactional
    public boolean artifacts(ThumbnailClaim claim, StorageKey thumbnail, StorageKey preview) {
        return jobs.lock(claim.mediaId()).filter(j -> j.owns(claim.token(), clock.instant())).map(j -> {
            j.artifacts(value(thumbnail), value(preview));
            return true;
        }).orElse(false);
    }

    @Transactional
    public boolean finish(ThumbnailClaim claim, ProcessedMedia processed) {
        StoredFile file = files.findByIdForUpdate(claim.mediaId()).orElse(null);
        ThumbnailJob job = jobs.lock(claim.mediaId()).orElse(null);
        if (job == null || !job.owns(claim.token(), clock.instant())) {
            cleanup(claim.mediaId(), processed.thumbnailKey(), processed.previewKey());
            return false;
        }
        if (file == null || file.getStatus() != UploadStatus.PROCESSING) {
            cleanup(claim.mediaId(), processed.thumbnailKey(), processed.previewKey());
            job.complete(file != null && file.getStatus() == UploadStatus.READY ? State.SUCCEEDED : State.CANCELLED,
                clock.instant(), null);
            return false;
        }
        finisher.finish(claim.mediaId(), processed);
        job.complete(State.SUCCEEDED, clock.instant(), null);
        metrics.timer("sssok.thumbnail.ready.latency", "kind", claim.kind().name().toLowerCase(java.util.Locale.ROOT))
            .record(Duration.between(job.getCreatedAt(), clock.instant()));
        log.info("썸네일 작업 완료 mediaId={} claimToken={} attempt={}", claim.mediaId(), claim.token(), claim.attempt());
        return true;
    }

    @Transactional
    public void failed(ThumbnailClaim claim, boolean permanent, String code) {
        StoredFile file = files.findByIdForUpdate(claim.mediaId()).orElse(null);
        jobs.lock(claim.mediaId()).filter(j -> j.owns(claim.token(), clock.instant()))
            .ifPresent(j -> {
                cleanup(claim.mediaId(), key(j.getThumbnailKey()), key(j.getPreviewKey()));
                failOrRetry(j, file, permanent, code);
            });
    }

    @Transactional
    public void skip(ThumbnailClaim claim) {
        StoredFile file = files.findByIdForUpdate(claim.mediaId()).orElse(null);
        jobs.lock(claim.mediaId()).filter(j -> j.owns(claim.token(), clock.instant()))
            .ifPresent(j -> j.complete(file != null && file.getStatus() == UploadStatus.READY
                ? State.SUCCEEDED : State.CANCELLED, clock.instant(), null));
    }

    @Transactional
    public void recover(Long mediaId) {
        StoredFile file = files.findByIdForUpdate(mediaId).orElse(null);
        jobs.lock(mediaId).filter(j -> j.getState() == State.RUNNING
            && !j.getLeaseUntil().isAfter(clock.instant())).ifPresent(j -> {
                cleanup(mediaId, key(j.getThumbnailKey()), key(j.getPreviewKey()));
                failOrRetry(j, file, false, "LEASE_EXPIRED");
                metrics.counter("sssok.thumbnail.recovered").increment();
            });
    }

    @Transactional
    public void backfill(Long mediaId) {
        files.findByIdForUpdate(mediaId).filter(f -> f.getStatus() == UploadStatus.PROCESSING)
            .ifPresent(this::enqueue);
    }

    public List<Long> expired() { return jobs.expired(clock.instant(), properties.recoveryBatchSize()); }
    public List<Long> missing() { return jobs.missing(properties.recoveryBatchSize()); }

    @Transactional
    public void cleanup(Long mediaId, StorageKey... keys) {
        StoredFile file = files.findByIdForUpdate(mediaId).orElse(null);
        // 완료 커밋 응답이 유실되어도 실제 채택된 파생본을 지우지 않는다.
        List<StorageKey> present = java.util.Arrays.stream(keys).filter(Objects::nonNull)
            .filter(k -> file == null || (!k.equals(file.getThumbnailKey()) && !k.equals(file.getPreviewKey())))
            .toList();
        if (!present.isEmpty()) { orphans.enqueue(present); }
    }

    private void failOrRetry(ThumbnailJob job, StoredFile file, boolean permanent, String code) {
        if (file == null || file.getStatus() != UploadStatus.PROCESSING) {
            job.complete(file != null && file.getStatus() == UploadStatus.READY ? State.SUCCEEDED : State.CANCELLED,
                clock.instant(), code);
        } else if (permanent || job.getAttempts() >= properties.maxAttempts()) {
            finisher.markFailed(job.getMediaId());
            job.complete(State.FAILED, clock.instant(), code);
            metrics.counter("sssok.thumbnail.failed").increment();
        } else {
            long ceiling = properties.retryBase().toMillis();
            for (int n = 1; n < job.getAttempts() && ceiling < properties.retryMax().toMillis(); n++) {
                ceiling = Math.min(properties.retryMax().toMillis(), ceiling * 2);
            }
            // equal jitter: 즉시 재시도 폭주를 피하면서 같은 시각에 작업이 몰리지 않게 한다.
            long delay = ThreadLocalRandom.current().nextLong(Math.max(1, ceiling / 2), ceiling + 1);
            job.retry(clock.instant().plusMillis(delay), code);
            metrics.counter("sssok.thumbnail.retried").increment();
        }
        log.info("썸네일 작업 상태 변경 mediaId={} state={} attempt={} reason={}",
            job.getMediaId(), job.getState(), job.getAttempts(), code);
    }

    private static String value(StorageKey key) { return key == null ? null : key.value(); }
    private static StorageKey key(String value) { return value == null ? null : new StorageKey(value); }
}
