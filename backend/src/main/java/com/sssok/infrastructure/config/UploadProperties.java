package com.sssok.infrastructure.config;

import com.sssok.domain.file.UploadSizePolicy;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

@ConfigurationProperties(prefix = "upload")
public record UploadProperties(Duration presignedUrlTtl, int maxRetryCount,
                               DataSize imageMaxSize, DataSize videoMaxSize) {

    public UploadProperties {
        imageMaxSize = imageMaxSize == null ? DataSize.ofMegabytes(20) : imageMaxSize;
        videoMaxSize = videoMaxSize == null ? DataSize.ofGigabytes(1) : videoMaxSize;
    }

    public UploadSizePolicy sizePolicy() {
        return new UploadSizePolicy(imageMaxSize.toBytes(), videoMaxSize.toBytes());
    }
}
