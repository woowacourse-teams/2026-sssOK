package com.sssok.presentation.api.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sssok.application.media.MediaCursor;
import com.sssok.application.media.exception.InvalidCursorException;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class MediaCursorCodecTest {

    private final MediaCursorCodec codec = new MediaCursorCodec(new ObjectMapper().findAndRegisterModules());

    @Test
    void 커서를_문자열로_인코딩하고_다시_복원한다() {
        MediaCursor cursor = new MediaCursor(1L, 10L, Instant.parse("2026-09-20T02:00:00Z"), 103L);

        String encoded = codec.encode(cursor);

        assertThat(codec.decode(encoded, 1L, 10L)).isEqualTo(cursor);
    }

    @Test
    void 올바르지_않은_문자열이면_예외가_발생한다() {
        assertThatThrownBy(() -> codec.decode("invalid-cursor", 1L, 10L))
            .isInstanceOf(InvalidCursorException.class);
    }

    @Test
    void JSON_null인_커서이면_예외가_발생한다() {
        assertThatThrownBy(() -> codec.decode("bnVsbA", 1L, 10L))
            .isInstanceOf(InvalidCursorException.class);
    }

    @Test
    void 다른_방에서_발급한_커서이면_예외가_발생한다() {
        String encoded = codec.encode(new MediaCursor(
            1L, 10L, Instant.parse("2026-09-20T02:00:00Z"), 103L));

        assertThatThrownBy(() -> codec.decode(encoded, 2L, 10L))
            .isInstanceOf(InvalidCursorException.class);
    }

    @Test
    void 다른_폴더에서_발급한_커서이면_예외가_발생한다() {
        String encoded = codec.encode(new MediaCursor(
            1L, 10L, Instant.parse("2026-09-20T02:00:00Z"), 103L));

        assertThatThrownBy(() -> codec.decode(encoded, 1L, 20L))
            .isInstanceOf(InvalidCursorException.class);
    }

    @Test
    void 방_전체_조회_커서는_folderId가_null이어야_한다() {
        String encoded = codec.encode(new MediaCursor(
            1L, null, Instant.parse("2026-09-20T02:00:00Z"), 103L));

        assertThat(codec.decode(encoded, 1L, null).folderId()).isNull();
    }
}
