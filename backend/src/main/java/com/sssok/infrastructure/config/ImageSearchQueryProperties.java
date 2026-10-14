package com.sssok.infrastructure.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "media.search.query")
public record ImageSearchQueryProperties(Double minSimilarity, Duration timeout) {
    public ImageSearchQueryProperties {
        timeout = timeout == null ? Duration.ofSeconds(30) : timeout;
        if (timeout.isZero() || timeout.isNegative()
            || (minSimilarity != null && (!Double.isFinite(minSimilarity)
                || minSimilarity < -1 || minSimilarity > 1))) {
            throw new IllegalArgumentException("검색 임계값과 호출 제한 시간을 확인해주세요");
        }
    }
}
