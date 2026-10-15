package com.sssok.common.monitoring;

import com.sssok.infrastructure.persistence.thumbnail.ThumbnailJob;
import com.sssok.infrastructure.persistence.thumbnail.ThumbnailJobRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ThumbnailQueueMetrics {
    private final ThumbnailJobRepository repository;
    private final EnumMap<ThumbnailJob.State, AtomicLong> counts = new EnumMap<>(ThumbnailJob.State.class);
    private final AtomicLong oldest = new AtomicLong();
    private final AtomicLong lastRefresh = new AtomicLong();

    public ThumbnailQueueMetrics(ThumbnailJobRepository repository, MeterRegistry registry) {
        this.repository = repository;
        for (ThumbnailJob.State state : ThumbnailJob.State.values()) {
            AtomicLong count = new AtomicLong();
            counts.put(state, count);
            Gauge.builder("sssok.thumbnail.jobs", count, AtomicLong::get)
                .tag("state", state.name().toLowerCase(Locale.ROOT)).register(registry);
        }
        Gauge.builder("sssok.thumbnail.wait.oldest", oldest, AtomicLong::get).baseUnit("seconds").register(registry);
        Gauge.builder("sssok.thumbnail.queue.last.refresh", lastRefresh, AtomicLong::get).baseUnit("seconds").register(registry);
    }

    // scrape마다 DB를 조회하지 않고 주기적으로 스냅샷을 갱신한다. 실패 시 last.refresh가 정체된다.
    @Scheduled(fixedDelayString = "${media.thumbnail.queue.metrics-interval:30s}", initialDelayString = "30s")
    public void refresh() {
        var snapshot = repository.counts();
        Instant waiting = repository.oldestWaiting();
        counts.values().forEach(c -> c.set(0));
        snapshot.forEach(row -> counts.get((ThumbnailJob.State) row[0]).set((Long) row[1]));
        oldest.set(waiting == null ? 0 : Math.max(0, Duration.between(waiting, Instant.now()).toSeconds()));
        lastRefresh.set(Instant.now().getEpochSecond());
    }
}
