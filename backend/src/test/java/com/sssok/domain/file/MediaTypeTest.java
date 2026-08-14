package com.sssok.domain.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sssok.domain.file.exception.UnsupportedMediaTypeException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class MediaTypeTest {

    @ParameterizedTest
    @CsvSource({
        "cat.jpg, JPEG",
        "cat.jpeg, JPEG",
        "cat.png, PNG",
        "cat.gif, GIF",
        "clip.mp4, MP4",
        "clip.webm, WEBM",
        "clip.mov, MOV",
    })
    void 파일명에서_형식을_판별한다(String fileName, MediaType expected) {
        assertThat(MediaType.fromFileName(fileName)).isEqualTo(expected);
    }

    @Test
    void 확장자_대소문자를_가리지_않는다() {
        assertThat(MediaType.fromFileName("CAT.JPG")).isEqualTo(MediaType.JPEG);
    }

    @Test
    void 이름에_점이_여러_개면_마지막_확장자를_쓴다() {
        assertThat(MediaType.fromFileName("2026.여름.휴가.png")).isEqualTo(MediaType.PNG);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "document.pdf",
        "archive.zip",
        "확장자없음",
    })
    void 지원하지_않는_형식이면_예외(String fileName) {
        assertThatThrownBy(() -> MediaType.fromFileName(fileName))
            .isInstanceOf(UnsupportedMediaTypeException.class);
    }

    @Test
    void 파일명이_null_이면_예외() {
        assertThatThrownBy(() -> MediaType.fromFileName(null))
            .isInstanceOf(UnsupportedMediaTypeException.class);
    }

    @Test
    void 이미지_상한은_10MB_다() {
        assertThat(MediaType.JPEG.maxBytes()).isEqualTo(10L * 1024 * 1024);
    }

    @Test
    void 영상_상한은_1GB_다() {
        assertThat(MediaType.MP4.maxBytes()).isEqualTo(1024L * 1024 * 1024);
    }

    @Test
    void GIF_만_애니메이션_보존_대상이다() {
        assertThat(MediaType.GIF.preservesAnimation()).isTrue();
        assertThat(MediaType.PNG.preservesAnimation()).isFalse();
    }

    @Test
    void 이미지와_영상을_구분한다() {
        assertThat(MediaType.PNG.isImage()).isTrue();
        assertThat(MediaType.PNG.isVideo()).isFalse();
        assertThat(MediaType.MP4.isVideo()).isTrue();
    }
}
