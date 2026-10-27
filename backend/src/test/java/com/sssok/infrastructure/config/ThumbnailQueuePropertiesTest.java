package com.sssok.infrastructure.config;

import com.sssok.renewal.outgoing.config.ThumbnailQueueProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ThumbnailQueuePropertiesTest {
    @Test
    void 생략된_설정의_기본값을_유지한다() {
        ThumbnailQueueProperties properties = new ThumbnailQueueProperties(0, 0, 0, null, null, null, null, 0);
        assertThat(properties.imageWorkers()).isEqualTo(4);
        assertThat(properties.videoWorkers()).isEqualTo(2);
        assertThat(properties.maxAttempts()).isEqualTo(5);
        assertThat(properties.lease()).isEqualTo(Duration.ofMinutes(2));
        assertThat(properties.maxRuntime()).isEqualTo(Duration.ofMinutes(10));
        assertThat(properties.retryBase()).isEqualTo(Duration.ofSeconds(5));
        assertThat(properties.retryMax()).isEqualTo(Duration.ofMinutes(5));
        assertThat(properties.recoveryBatchSize()).isEqualTo(50);
        assertThat(properties.heartbeatInterval()).isEqualTo(Duration.ofSeconds(40));
    }

    @Test
    void 허용_경계와_밀리초_단위_heartbeat_계산을_유지한다() {
        ThumbnailQueueProperties properties = new ThumbnailQueueProperties(1, 1, 1,
            Duration.ofMillis(3001), Duration.ofMillis(3001), Duration.ofNanos(1), Duration.ofNanos(1), 1);
        assertThat(properties.heartbeatInterval()).isEqualTo(Duration.ofMillis(1000));
    }

    @ParameterizedTest
    @CsvSource({
        "-1,1,1,3000,3000,1,1,1,image-workers",
        "1,-1,1,3000,3000,1,1,1,video-workers",
        "1,1,-1,3000,3000,1,1,1,max-attempts",
        "1,1,1,2999,3000,1,1,1,lease",
        "1,1,1,3000,2999,1,1,1,max-runtime",
        "1,1,1,3000,3000,0,1,1,retry-base",
        "1,1,1,3000,3000,-1,1,1,retry-base",
        "1,1,1,3000,3000,2,1,1,retry-max",
        "1,1,1,3000,3000,1,1,-1,recovery-batch-size"
    })
    void 잘못된_설정은_항목을_명시해_거절한다(int images, int videos, int attempts,
            long lease, long maxRuntime, long retryBase, long retryMax, int batch, String setting) {
        assertThatThrownBy(() -> new ThumbnailQueueProperties(images, videos, attempts,
            Duration.ofMillis(lease), Duration.ofMillis(maxRuntime), Duration.ofMillis(retryBase),
            Duration.ofMillis(retryMax), batch))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining(setting);
    }
}
