package com.sssok.domain.file;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

// 상세 모달용 파생본을 만들지 말지. 만들지 않기로 한 것들은 상세 화면이 원본을 쓴다.
class PreviewNecessityTest {

    @ParameterizedTest
    @EnumSource(value = MediaType.class, names = {"JPEG", "PNG"})
    void 정지_사진은_프리뷰를_만든다(MediaType mediaType) {
        assertThat(file(mediaType).needsPreview()).isTrue();
    }

    // 파생본이 첫 프레임만 남은 정지 이미지라, 상세 화면에 띄우면 움직이던 것이 멈춘다.
    @Test
    void GIF는_프리뷰를_만들지_않는다() {
        assertThat(file(MediaType.GIF).needsPreview()).isFalse();
    }

    // 상세 화면이 원본을 재생하므로 중간 해상도 정지 이미지를 끼울 자리가 없다.
    @ParameterizedTest
    @EnumSource(value = MediaType.class, names = {"MP4", "WEBM", "MOV"})
    void 영상은_프리뷰를_만들지_않는다(MediaType mediaType) {
        assertThat(file(mediaType).needsPreview()).isFalse();
    }

    private StoredFile file(MediaType mediaType) {
        Instant now = Instant.now();
        return StoredFile.reconstruct(1L, 1L, 1L, "파일." + mediaType.extension(), mediaType,
            new FileSize(1024), new StorageKey("rooms/1/1." + mediaType.extension()), null,
            UploadStatus.READY, now, now, 0, null, null, null, null, null, null, null);
    }
}
