package com.sssok.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.sssok.domain.file.MediaType;
import com.sssok.domain.file.UploadSizePolicy;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

class UploadPropertiesTest {

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
}
