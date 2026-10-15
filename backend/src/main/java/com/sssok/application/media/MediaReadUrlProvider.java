package com.sssok.application.media;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import com.sssok.application.port.out.FileStoragePort;
import com.sssok.application.storage.ObjectsOrphanedEvent;
import com.sssok.domain.file.StorageKey;
import com.sssok.infrastructure.config.MediaUrlCacheProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 열람 URL만 재사용한다. 권한 및 미디어 상태 검사는 호출 전에 수행한다. */
@Component
public class MediaReadUrlProvider {
    private final FileStoragePort storage;
    private final MediaUrlCacheProperties properties;
    private final Clock clock;
    private final Cache<Key, MediaUrlSource.ResolvedUrl> cache;
    private final Counter hits;
    private final Counter generations;
    private final Counter refreshes;
    private final Counter failures;

    @Autowired
    public MediaReadUrlProvider(FileStoragePort storage, MediaUrlCacheProperties properties,
                               MeterRegistry registry) {
        this(storage, properties, registry, Clock.systemUTC());
    }

    MediaReadUrlProvider(FileStoragePort storage, MediaUrlCacheProperties properties,
                         MeterRegistry registry, Clock clock) {
        this.storage = storage;
        this.properties = properties;
        this.clock = clock;
        Counter evictions = registry.counter("media.read.url.cache.evictions");
        // TTL 정리와 별개로 반환 직전에도 시계 기준 유효기간을 검사한다.
        this.cache = Caffeine.newBuilder().maximumSize(properties.maximumSize())
            .expireAfter(new Expiry<Key, MediaUrlSource.ResolvedUrl>() {
                @Override
                public long expireAfterCreate(Key key, MediaUrlSource.ResolvedUrl value, long time) {
                    return remainingNanos(value);
                }

                @Override
                public long expireAfterUpdate(Key key, MediaUrlSource.ResolvedUrl value,
                                              long time, long currentDuration) {
                    return remainingNanos(value);
                }

                @Override
                public long expireAfterRead(Key key, MediaUrlSource.ResolvedUrl value,
                                            long time, long currentDuration) {
                    return currentDuration;
                }

                private long remainingNanos(MediaUrlSource.ResolvedUrl value) {
                    return Math.max(0, Duration.between(clock.instant(), value.expiresAt()).toNanos());
                }
            })
            .evictionListener((key, value, cause) -> evictions.increment())
            .build();
        registry.gauge("media.read.url.cache.size", cache, Cache::estimatedSize);
        hits = registry.counter("media.read.url.cache.hits");
        generations = registry.counter("media.read.url.generations");
        refreshes = registry.counter("media.read.url.cache.refreshes");
        failures = registry.counter("media.read.url.failures");
    }

    MediaUrlSource.ResolvedUrl resolve(StorageKey storageKey, String disposition,
                                      String contentType, Duration ttl) {
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("서명 URL 수명은 양수여야 합니다.");
        }
        Key key = new Key(storageKey, disposition, contentType, ttl);
        // 여유 시간보다 짧은 수명은 재사용할 수 없다.
        if (!properties.enabled() || ttl.compareTo(properties.refreshAhead()) <= 0) {
            return generate(key);
        }
        var cached = cache.getIfPresent(key);
        if (reusable(cached)) {
            hits.increment();
            return cached;
        }
        return cache.asMap().compute(key, (ignored, existing) -> {
            // 대기하는 동안 다른 요청이 갱신했을 수 있으므로 잠금 안에서 다시 확인한다.
            if (reusable(existing)) {
                hits.increment();
                return existing;
            }
            if (existing != null) {
                refreshes.increment();
            }
            return generate(key);
        });
    }

    private boolean reusable(MediaUrlSource.ResolvedUrl value) {
        return value != null && clock.instant().isBefore(
            value.expiresAt().minus(properties.refreshAhead()));
    }

    private MediaUrlSource.ResolvedUrl generate(Key key) {
        // 서명 완료 뒤의 시각으로 수명을 늘리지 않는다.
        Instant startedAt = clock.instant();
        generations.increment();
        try {
            String url = storage.presignGet(key.storageKey(), key.disposition(),
                key.contentType(), key.ttl());
            if (url == null || url.isBlank()) {
                throw new IllegalStateException("서명 URL이 비어 있습니다.");
            }
            Instant expiresAt = startedAt.plus(key.ttl());
            if (!clock.instant().isBefore(expiresAt)) {
                throw new IllegalStateException("서명 생성 중 URL 수명이 경과했습니다.");
            }
            return new MediaUrlSource.ResolvedUrl(url, expiresAt);
        } catch (RuntimeException exception) {
            failures.increment();
            throw exception;
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onObjectsOrphaned(ObjectsOrphanedEvent event) {
        var keys = new HashSet<>(event.storageKeys());
        cache.asMap().keySet().removeIf(key -> keys.contains(key.storageKey()));
    }

    private record Key(StorageKey storageKey, String disposition, String contentType, Duration ttl) {
    }
}
