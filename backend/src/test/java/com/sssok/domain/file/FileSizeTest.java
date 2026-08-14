package com.sssok.domain.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sssok.domain.file.exception.InvalidFileSizeException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class FileSizeTest {

    @Test
    void 킬로바이트와_메가바이트로_만들_수_있다() {
        assertThat(FileSize.ofKilobytes(1).bytes()).isEqualTo(1024);
        assertThat(FileSize.ofMegabytes(1).bytes()).isEqualTo(1024 * 1024);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    void 크기가_0_이하면_예외(long bytes) {
        assertThatThrownBy(() -> new FileSize(bytes))
            .isInstanceOf(InvalidFileSizeException.class);
    }

    @Test
    void 상한_초과_여부를_판별한다() {
        FileSize size = FileSize.ofMegabytes(11);

        assertThat(size.exceeds(FileSize.ofMegabytes(10).bytes())).isTrue();
        assertThat(size.exceeds(FileSize.ofMegabytes(11).bytes())).isFalse();
    }

    @Test
    void 크기가_500KB_미만인지_판별한다() {
        assertThat(FileSize.ofKilobytes(499).isBelowCompressionThreshold()).isTrue();
        assertThat(FileSize.ofKilobytes(500).isBelowCompressionThreshold()).isFalse();
    }
}
