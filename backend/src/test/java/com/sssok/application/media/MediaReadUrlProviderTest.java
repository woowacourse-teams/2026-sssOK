package com.sssok.application.media;

import com.sssok.application.storage.ObjectsOrphanedEvent;
import com.sssok.domain.file.StorageKey;
import com.sssok.domain.file.StoredFile;
import com.sssok.infrastructure.config.MediaUrlCacheProperties;
import com.sssok.infrastructure.config.ThumbnailProperties;
import com.sssok.renewal.outgoing.storage.FileStorage;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;


class MediaReadUrlProviderTest {
    private static final Duration TTL = Duration.ofMinutes(30);
    private static final Instant START = Instant.parse("2026-10-15T00:00:00Z");
    private static final StorageKey KEY = new StorageKey("rooms/1/previews/photo.webp");
    private final FileStorage storage = mock(FileStorage.class);
    private final Clock clock = mock(Clock.class);
    private final AtomicReference<Instant> now = new AtomicReference<>(START);
    private final SimpleMeterRegistry metrics = new SimpleMeterRegistry();
    private MediaReadUrlProvider provider;

    @BeforeEach
    void setUp() {
        when(clock.instant()).thenAnswer(call -> now.get());
        AtomicInteger sequence = new AtomicInteger();
        when(storage.presignGet(any(), anyString(), anyString(), any()))
            .thenAnswer(call -> "https://signed/" + sequence.incrementAndGet());
        provider = new MediaReadUrlProvider(storage,
            new MediaUrlCacheProperties(true, 100L, Duration.ofMinutes(2)), metrics, clock);
    }

    private MediaUrlSource.ResolvedUrl resolve() {
        return provider.resolve(KEY, "inline", "image/webp", TTL);
    }

    @Test
    void 반복_조회는_원래_URL과_만료시각을_유지한다() {
        var first = resolve();
        now.set(START.plus(Duration.ofMinutes(27)));
        assertThat(resolve()).isEqualTo(first);
        assertThat(first.expiresAt()).isEqualTo(START.plus(TTL));
        verify(storage, times(1)).presignGet(any(), anyString(), anyString(), any());
        assertThat(metrics.get("media.read.url.cache.hits").counter().count()).isEqualTo(1);
    }

    @Test
    void 갱신_경계부터_새_URL을_생성한다() {
        var first = resolve();
        now.set(START.plus(Duration.ofMinutes(28)));
        var renewed = resolve();
        assertThat(renewed.url()).isNotEqualTo(first.url());
        assertThat(renewed.expiresAt()).isEqualTo(now.get().plus(TTL));
        assertThat(resolve()).isEqualTo(renewed);
        assertThat(metrics.get("media.read.url.cache.refreshes").counter().count()).isEqualTo(1);
    }

    @Test
    void 이미_만료한_URL도_다시_생성한다() {
        var first = resolve();
        now.set(START.plus(Duration.ofHours(1)));
        assertThat(resolve().url()).isNotEqualTo(first.url());
    }

    @Test
    void 파일_헤더_수명이_다르면_구분한다() {
        resolve();
        provider.resolve(new StorageKey("other"), "inline", "image/webp", TTL);
        provider.resolve(KEY, "attachment", "image/webp", TTL);
        provider.resolve(KEY, "inline", "image/jpeg", TTL);
        provider.resolve(KEY, "inline", "image/webp", Duration.ofMinutes(10));
        verify(storage, times(5)).presignGet(any(), anyString(), anyString(), any());
    }

    @Test
    void 최초_동시_조회와_만료_동시_갱신은_한번만_서명한다() throws Exception {
        assertConcurrentSingleResult();
        verify(storage, times(1)).presignGet(any(), anyString(), anyString(), any());
        now.set(START.plus(Duration.ofMinutes(28)));
        assertConcurrentSingleResult();
        verify(storage, times(2)).presignGet(any(), anyString(), anyString(), any());
    }

    private void assertConcurrentSingleResult() throws Exception {
        CountDownLatch ready = new CountDownLatch(20);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(20)) {
            List<Future<MediaUrlSource.ResolvedUrl>> futures = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("timeout");
                    return resolve();
                }));
            }
            try {
                assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            } finally {
                start.countDown();
            }
            var expected = futures.getFirst().get(5, TimeUnit.SECONDS);
            for (var future : futures) assertThat(future.get(5, TimeUnit.SECONDS)).isEqualTo(expected);
        }
    }

    @Test
    void 생성_실패는_캐시하지_않는다() {
        when(storage.presignGet(any(), anyString(), anyString(), any()))
            .thenThrow(new IllegalStateException("failed")).thenReturn("recovered");
        assertThatThrownBy(this::resolve).isInstanceOf(IllegalStateException.class);
        assertThat(resolve().url()).isEqualTo("recovered");
        assertThat(metrics.get("media.read.url.failures").counter().count()).isEqualTo(1);
    }

    @Test
    void 갱신_실패_후_오래된_URL을_반환하지_않고_재시도한다() {
        resolve();
        now.set(START.plus(TTL));
        when(storage.presignGet(any(), anyString(), anyString(), any()))
            .thenThrow(new IllegalStateException("failed")).thenReturn("renewed");
        assertThatThrownBy(this::resolve).isInstanceOf(IllegalStateException.class);
        assertThat(resolve().url()).isEqualTo("renewed");
    }

    @Test
    void 비활성화와_재시작은_새로_서명한다() {
        resolve();
        provider = new MediaReadUrlProvider(storage,
            new MediaUrlCacheProperties(false, 100L, Duration.ofMinutes(2)),
            new SimpleMeterRegistry(), clock);
        resolve();
        resolve();
        verify(storage, times(3)).presignGet(any(), anyString(), anyString(), any());
    }

    @Test
    void 생성에_걸린_시간만큼_만료를_연장하지_않는다() {
        when(storage.presignGet(any(), anyString(), anyString(), any())).thenAnswer(call -> {
            now.set(START.plusSeconds(10));
            return "signed";
        });
        assertThat(resolve().expiresAt()).isEqualTo(START.plus(TTL));
    }

    @Test
    void 생성_도중_수명이_지난_URL은_반환하지_않는다() {
        when(storage.presignGet(any(), anyString(), anyString(), any())).thenAnswer(call -> {
            now.set(START.plus(TTL));
            return "signed";
        });
        assertThatThrownBy(this::resolve).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 삭제된_파일의_모든_서명_조건을_제거한다() {
        resolve();
        provider.resolve(KEY, "inline", "image/jpeg", TTL);
        provider.onObjectsOrphaned(new ObjectsOrphanedEvent(List.of(KEY)));
        resolve();
        provider.resolve(KEY, "inline", "image/jpeg", TTL);
        verify(storage, times(4)).presignGet(any(), anyString(), anyString(), any());
    }

    @Test
    void 여유시간보다_짧은_URL은_재사용하지_않는다() {
        provider.resolve(KEY, "inline", "image/webp", Duration.ofMinutes(1));
        provider.resolve(KEY, "inline", "image/webp", Duration.ofMinutes(1));
        verify(storage, times(2)).presignGet(any(), anyString(), anyString(), any());
    }

    @Test
    void 캐시_상한을_넘으면_퇴출하고_필요시_재생성한다() {
        var boundedMetrics = new SimpleMeterRegistry();
        provider = new MediaReadUrlProvider(storage,
            new MediaUrlCacheProperties(true, 2L, Duration.ofMinutes(2)),
            boundedMetrics, clock);
        for (int i = 0; i < 50; i++) {
            provider.resolve(new StorageKey("bounded/" + i), "inline", "image/webp", TTL);
        }
        Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            assertThat(boundedMetrics.get("media.read.url.cache.evictions").counter().count()).isPositive();
            assertThat(boundedMetrics.get("media.read.url.cache.size").gauge().value()).isLessThanOrEqualTo(2);
        });
        clearInvocations(storage);
        for (int i = 0; i < 50; i++) {
            provider.resolve(new StorageKey("bounded/" + i), "inline", "image/webp", TTL);
        }
        verify(storage, atLeastOnce()).presignGet(any(), anyString(), anyString(), any());
    }

    @Test
    void 재시작된_캐시는_빈_상태에서_생성한다() {
        var first = resolve();
        provider = new MediaReadUrlProvider(storage,
            new MediaUrlCacheProperties(true, 100L, Duration.ofMinutes(2)),
            new SimpleMeterRegistry(), clock);
        assertThat(resolve().url()).isNotEqualTo(first.url());
    }

    @Test
    void 사진_300장_목록을_8회_조립해도_서명은_600회다() {
        provider = new MediaReadUrlProvider(storage,
            new MediaUrlCacheProperties(true, 1_000L, Duration.ofMinutes(2)),
            new SimpleMeterRegistry(), clock);
        var resolver = new MediaUrlResolver(provider,
            new ThumbnailProperties(TTL));
        List<StoredFile> files = new ArrayList<>();
        for (int i = 0; i < 300; i++) {
            var file = mock(StoredFile.class);
            when(file.getThumbnailKey()).thenReturn(new StorageKey("thumbnails/" + i + ".webp"));
            when(file.getPreviewKey()).thenReturn(new StorageKey("previews/" + i + ".webp"));
            files.add(file);
        }
        for (int i = 0; i < 8; i++) files.forEach(resolver::resolve);
        verify(storage, times(600)).presignGet(any(), anyString(), anyString(), any());
    }

    @Test
    void 잘못된_설정은_시작할_수_없다() {
        assertThatThrownBy(() -> new MediaUrlCacheProperties(true, 0L, Duration.ZERO))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MediaUrlCacheProperties(true, 1L, Duration.ofSeconds(-1)))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
