package com.sssok.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sssok.domain.file.MediaType;
import com.sssok.domain.file.StorageKey;
import com.sssok.infrastructure.config.R2Properties;
import java.time.Duration;
import org.junit.jupiter.api.Test;

// 자격증명이 비어 있어도 빈이 만들어져야 한다. 생성자에서 AWS 클라이언트를 만들면
// 자격증명이 없는 환경에서 스프링 컨텍스트가 통째로 뜨지 않아, R2 를 쓰지 않는 테스트까지 죽는다.
class R2FileStorageAdapterLazyInitTest {

    private static final R2Properties EMPTY =
        new R2Properties("https://dummy.r2.cloudflarestorage.com", null, null, null, null);

    @Test
    void 자격증명이_없어도_빈은_만들어진다() {
        assertThatCode(() -> new R2FileStorageAdapter(EMPTY)).doesNotThrowAnyException();
    }

    @Test
    void 실제로_쓸_때_비로소_자격증명을_요구한다() {
        R2FileStorageAdapter adapter = new R2FileStorageAdapter(EMPTY);

        assertThatThrownBy(() -> adapter.presignPut(
            StorageKey.generate(1L, MediaType.PNG), "image/png", Duration.ofMinutes(10)))
            .isInstanceOf(RuntimeException.class);
    }
}
