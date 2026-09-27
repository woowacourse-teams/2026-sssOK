package com.sssok.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.sssok.domain.file.MediaType;
import com.sssok.domain.file.UploadSizePolicy;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.util.unit.DataSize;

class UploadPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withUserConfiguration(TestConfig.class);

    @Test
    void 설정한_상한이_그대로_정책이_된다() {
        UploadProperties properties = new UploadProperties(Duration.ofMinutes(10), 5,
            DataSize.ofMegabytes(20), DataSize.ofGigabytes(1));

        UploadSizePolicy policy = properties.sizePolicy();

        assertThat(policy.maxBytesFor(MediaType.JPEG)).isEqualTo(20L * 1024 * 1024);
        assertThat(policy.maxBytesFor(MediaType.MP4)).isEqualTo(1024L * 1024 * 1024);
    }

    @Test
    void 상한이_비어_있으면_기본값을_쓴다() {
        UploadProperties properties = new UploadProperties(Duration.ofMinutes(10), 5, null, null);

        assertThat(properties.imageMaxSize()).isEqualTo(DataSize.ofMegabytes(20));
        assertThat(properties.videoMaxSize()).isEqualTo(DataSize.ofGigabytes(1));
    }

    // 이 PR 의 출발점이 "yml 에 있는데 아무도 읽지 않던 설정"이었다.
    // 프로퍼티 이름이 어긋나거나 필드가 빠지면 여기서 걸린다.
    @Test
    void yml_의_상한이_실제로_바인딩된다() {
        contextRunner
            .withPropertyValues(
                "upload.presigned-url-ttl=10m",
                "upload.max-retry-count=5",
                "upload.image-max-size=30MB",
                "upload.video-max-size=2GB")
            .run(context -> {
                UploadProperties properties = context.getBean(UploadProperties.class);

                assertThat(properties.presignedUrlTtl()).isEqualTo(Duration.ofMinutes(10));
                assertThat(properties.maxRetryCount()).isEqualTo(5);
                assertThat(properties.imageMaxSize()).isEqualTo(DataSize.ofMegabytes(30));
                assertThat(properties.videoMaxSize()).isEqualTo(DataSize.ofGigabytes(2));
            });
    }

    @Test
    void 상한이_0_이하로_설정되면_기동에_실패한다() {
        contextRunner
            .withPropertyValues(
                "upload.presigned-url-ttl=10m",
                "upload.max-retry-count=5",
                "upload.image-max-size=0B",
                "upload.video-max-size=1GB")
            .run(context -> assertThat(context)
                .hasFailed()
                .getFailure()
                .rootCause()
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("upload.image-max-size"));
    }

    @EnableConfigurationProperties(UploadProperties.class)
    static class TestConfig {
    }
}