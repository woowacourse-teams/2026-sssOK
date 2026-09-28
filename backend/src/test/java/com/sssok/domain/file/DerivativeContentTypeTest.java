package com.sssok.domain.file;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

class DerivativeContentTypeTest {

    @ParameterizedTest
    @CsvSource({
        "rooms/1/thumbnails/a.webp, image/webp",
        "rooms/1/thumbnails/a.jpg, image/jpeg",
        "rooms/1/thumbnails/a.jpeg, image/jpeg",
        "rooms/1/thumbnails/a.png, image/png",
        "rooms/1/thumbnails/a.gif, image/gif",
        "rooms/1/thumbnails/A.WEBP, image/webp"
    })
    void 키의_확장자에서_Content_Type을_읽는다(String key, String expected) {
        assertThat(DerivativeContentType.of(new StorageKey(key))).isEqualTo(expected);
    }

    // 포맷을 추가하면서 여기 매핑을 빠뜨리는 일이 없도록, enum 쪽을 기준으로 한 번 더 건다.
    @ParameterizedTest
    @EnumSource(DerivativeFormat.class)
    void 파생본_포맷은_모두_제_Content_Type으로_읽힌다(DerivativeFormat format) {
        StorageKey key = new StorageKey("rooms/1/thumbnails/a." + format.extension());

        assertThat(DerivativeContentType.of(key)).isEqualTo(format.contentType());
    }

    // 알 수 없는 확장자를 image/jpeg 로 찍으면 실제 포맷과 어긋난 채 브라우저가 그리려다 깨진다.
    @ParameterizedTest
    @CsvSource({"rooms/1/thumbnails/a.bmp", "rooms/1/thumbnails/noext"})
    void 알_수_없는_확장자는_이미지로_단정하지_않는다(String key) {
        assertThat(DerivativeContentType.of(new StorageKey(key)))
            .isEqualTo("application/octet-stream");
    }
}