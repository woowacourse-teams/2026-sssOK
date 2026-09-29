package com.sssok.application.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.sssok.application.port.out.FileStoragePort;
import com.sssok.domain.file.MediaType;
import com.sssok.domain.file.StorageKey;
import com.sssok.domain.file.StoredFile;
import com.sssok.domain.file.UploadStatus;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class MediaUrlSourceTest {

    private static final Duration DISPLAY_TTL = Duration.ofMinutes(30);
    private static final Instant NOW = Instant.parse("2026-09-28T12:00:00Z");
    private static final String SIGNED_URL = "https://storage.example.com/signed";

    private final FileStoragePort storage = mock(FileStoragePort.class);

    @Test
    void 프리뷰는_화면_표시용_TTL로_서명한다() {
        StoredFile file = mock(StoredFile.class);
        StorageKey previewKey = new StorageKey("rooms/1/previews/photo.webp");
        given(file.getPreviewKey()).willReturn(previewKey);
        given(storage.presignGet(previewKey, "inline", "image/webp", DISPLAY_TTL))
            .willReturn(SIGNED_URL);

        MediaUrlSource.ResolvedUrl resolved = MediaUrlSource.display(file, DISPLAY_TTL)
            .resolve(storage, NOW);

        assertThat(resolved.url()).isEqualTo(SIGNED_URL);
        assertThat(resolved.expiresAt()).isEqualTo(NOW.plus(DISPLAY_TTL));
        verify(storage).presignGet(previewKey, "inline", "image/webp", DISPLAY_TTL);
    }

    @Test
    void 원본도_다운로드용이_아닌_화면_표시용_TTL로_서명한다() {
        StoredFile file = mock(StoredFile.class);
        StorageKey originalKey = new StorageKey("rooms/1/videos/video.mp4");
        given(file.getPreviewKey()).willReturn(null);
        given(file.getStatus()).willReturn(UploadStatus.READY);
        given(file.getStorageKey()).willReturn(originalKey);
        given(file.getMediaType()).willReturn(MediaType.MP4);
        given(storage.presignGet(originalKey, "inline", "video/mp4", DISPLAY_TTL))
            .willReturn(SIGNED_URL);

        MediaUrlSource.ResolvedUrl resolved = MediaUrlSource.display(file, DISPLAY_TTL)
            .resolve(storage, NOW);

        assertThat(resolved.url()).isEqualTo(SIGNED_URL);
        assertThat(resolved.expiresAt()).isEqualTo(NOW.plus(DISPLAY_TTL));
        verify(storage).presignGet(originalKey, "inline", "video/mp4", DISPLAY_TTL);
    }
}
