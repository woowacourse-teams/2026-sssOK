package com.sssok.renewal.outgoing.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "download")
public record DownloadProperties(Duration presignedGetTtl, int maxConcurrentJobsPerRequester, Duration retention) {
}
