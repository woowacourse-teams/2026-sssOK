package com.sssok.renewal.domain.media.thumbnail;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class ThumbnailRetryPolicyTest {
    private final ThumbnailRetryPolicy policy = new ThumbnailRetryPolicy(3, Duration.ofSeconds(5), Duration.ofSeconds(20));

    @Test
    void 영구오류와_시도한도는_재시도를_중단한다() {
        assertThat(policy.shouldStop(1, true)).isTrue();
        assertThat(policy.shouldStop(2, false)).isFalse();
        assertThat(policy.shouldStop(3, false)).isTrue();
    }

    @Test
    void 지수백오프와_jitter는_기존_범위와_상한을_유지한다() {
        assertThat(policy.nextDelay(1).toMillis()).isBetween(2500L, 5000L);
        assertThat(policy.nextDelay(2).toMillis()).isBetween(5000L, 10000L);
        assertThat(policy.nextDelay(3).toMillis()).isBetween(10000L, 20000L);
        assertThat(policy.nextDelay(100).toMillis()).isBetween(10000L, 20000L);
    }
}
