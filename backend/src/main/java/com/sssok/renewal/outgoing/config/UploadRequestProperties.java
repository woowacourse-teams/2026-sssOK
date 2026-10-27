package com.sssok.renewal.outgoing.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "notification.upload-request")
public record UploadRequestProperties(Duration cooldown) {
}
