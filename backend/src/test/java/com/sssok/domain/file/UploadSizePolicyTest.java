package com.sssok.domain.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class UploadSizePolicyTest {

    private static final long IMAGE_MAX = 20L * 1024 * 1024;
    private static final long VIDEO_MAX = 1024L * 1024 * 1024;

    @Test
    void 이미지와_영상의_상한을_따로_돌려준다() {
        UploadSizePolicy policy = new UploadSizePolicy(IMAGE_MAX, VIDEO_MAX);

        assertThat(policy.maxBytesFor(MediaType.JPEG)).isEqualTo(IMAGE_MAX);
        assertThat(policy.maxBytesFor(MediaType.MP4)).isEqualTo(VIDEO_MAX);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    void 상한이_0_이하면_예외(long bytes) {
        assertThatThrownBy(() -> new UploadSizePolicy(bytes, VIDEO_MAX))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new UploadSizePolicy(IMAGE_MAX, bytes))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
