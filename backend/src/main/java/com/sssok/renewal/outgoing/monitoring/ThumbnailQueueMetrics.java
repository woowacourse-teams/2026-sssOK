package com.sssok.renewal.outgoing.monitoring;

import com.sssok.renewal.domain.media.thumbnail.status.ThumbnailJobStatus;
import com.sssok.renewal.outgoing.database.ThumbnailJobRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ThumbnailQueueMetrics {
    private final ThumbnailJobRepository repository;
    private final Map<ThumbnailJobStatus, AtomicLong> counts = new LinkedHashMap<>();
    private final AtomicLong oldest = new AtomicLong();
    private final AtomicLong lastRefresh = new AtomicLong();

    public ThumbnailQueueMetrics(ThumbnailJobRepository repository, MeterRegistry registry) {
        this.repository = repository;
        for (ThumbnailJobStatus state : ThumbnailJobStatus.all()) {
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
        snapshot.forEach(row -> counts.get(ThumbnailJobStatus.from((String) row[0])).set((Long) row[1]));
        oldest.set(waiting == null ? 0 : Math.max(0, Duration.between(waiting, Instant.now()).toSeconds()));
        lastRefresh.set(Instant.now().getEpochSecond());
    }
}
