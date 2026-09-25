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
        // 잘못 잡힌 상한은 첫 업로드 요청의 500 이 아니라 기동 실패로 드러나야 배포 시점에 잡힌다.
        requirePositive(imageMaxSize, "upload.image-max-size");
        requirePositive(videoMaxSize, "upload.video-max-size");
    }

    private static void requirePositive(DataSize size, String key) {
        if (size.toBytes() <= 0) {
            throw new IllegalArgumentException(key + " 는 0보다 커야 합니다: " + size.toBytes());
        }
    }

    public UploadSizePolicy sizePolicy() {
        return new UploadSizePolicy(imageMaxSize.toBytes(), videoMaxSize.toBytes());
    }
}
