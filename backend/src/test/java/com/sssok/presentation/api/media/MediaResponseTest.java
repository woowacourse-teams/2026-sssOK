package com.sssok.presentation.api.media;

import static org.assertj.core.api.Assertions.assertThat;

import com.sssok.application.media.MediaDetail;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class MediaResponseTest {

    private static final Instant EXPIRES_AT = Instant.parse("2026-09-28T12:30:00Z");
    private static final String PREVIEW_URL = "https://storage.example.com/preview";
    @Test
    void 응용_계층의_displayUrl을_그대로_반환한다() {
        MediaResponse response = MediaResponse.from(media(PREVIEW_URL, EXPIRES_AT));

        assertThat(response.displayUrl()).isEqualTo(PREVIEW_URL);
        assertThat(response.displayUrlExpiresAt()).isEqualTo(EXPIRES_AT);
    }

    @Test
    void 표시할_URL이_없으면_URL과_만료시각이_함께_비어있다() {
        MediaResponse response = MediaResponse.from(media(null, null));

        assertThat(response.displayUrl()).isNull();
        assertThat(response.displayUrlExpiresAt()).isNull();
    }

    private MediaDetail media(String displayUrl, Instant displayExpiresAt) {
        return new MediaDetail(
            1L, "IMAGE", "사진.jpg", "image/jpeg", 1024L,
            "https://storage.example.com/thumbnail", EXPIRES_AT,
            displayUrl, displayExpiresAt,
            1200, 900, null, List.of(), 7L, "가현", "READY", Instant.now(), 0, false);
    }
}
