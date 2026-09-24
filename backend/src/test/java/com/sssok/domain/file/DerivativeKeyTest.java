package com.sssok.domain.file;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

// 파생본 키는 스토리지에서 원본과 짝이 눈에 보여야 하고, 무엇이 들었는지 확장자가 말해야 한다.
class DerivativeKeyTest {

    private static final StorageKey ORIGINAL = new StorageKey("rooms/7/abc-123.jpg");

    @Test
    void 썸네일은_원본_옆_thumbnails_아래에_같은_이름으로_놓인다() {
        assertThat(ORIGINAL.thumbnail("webp"))
            .isEqualTo(new StorageKey("rooms/7/thumbnails/abc-123.webp"));
    }

    @Test
    void 프리뷰는_같은_규칙으로_previews_아래에_놓인다() {
        assertThat(ORIGINAL.preview("webp"))
            .isEqualTo(new StorageKey("rooms/7/previews/abc-123.webp"));
    }

    // 확장자가 원본을 따라가면 내용은 WebP 인데 키는 .jpg 로 끝난다. 스토리지를 직접 들여다볼 때
    // 무엇이 들었는지 알 수 없고, Content-Type 을 키에서 되읽을 수도 없다.
    @Test
    void 확장자는_원본이_아니라_파생본_포맷을_따른다() {
        StorageKey pngOriginal = new StorageKey("rooms/7/abc-123.png");

        assertThat(pngOriginal.thumbnail("webp").value()).endsWith(".webp");
    }

    // 영상 썸네일은 ffmpeg 이 뽑은 JPEG 프레임이라 파생본 포맷 설정을 따르지 않는다.
    @Test
    void 영상_썸네일은_JPEG_확장자를_받는다() {
        StorageKey video = new StorageKey("rooms/7/abc-123.mp4");

        assertThat(video.thumbnail("jpg"))
            .isEqualTo(new StorageKey("rooms/7/thumbnails/abc-123.jpg"));
    }

    // 서명에 쓸 형식을 지금 설정값에서 읽으면, 포맷을 바꾼 뒤 예전 파생본이 전부 틀린
    // Content-Type 으로 서명돼 깨진다. 키 자체가 답을 갖고 있어야 한다.
    @Test
    void Content_Type을_키의_확장자에서_읽는다() {
        assertThat(DerivativeContentType.of(ORIGINAL.thumbnail("webp"))).isEqualTo("image/webp");
        assertThat(DerivativeContentType.of(ORIGINAL.thumbnail("jpg"))).isEqualTo("image/jpeg");
        assertThat(DerivativeContentType.of(new StorageKey("rooms/7/thumbnails/a.png")))
            .isEqualTo("image/png");
        assertThat(DerivativeContentType.of(new StorageKey("rooms/7/thumbnails/a.gif")))
            .isEqualTo("image/gif");
    }

    // 여기서 예외를 던지면 사진 한 장 때문에 목록 조회 전체가 500 이 된다.
    @Test
    void 확장자를_읽을_수_없는_키도_형식을_돌려준다() {
        assertThat(DerivativeContentType.of(new StorageKey("rooms/7/thumbnails/확장자없음")))
            .isEqualTo("image/jpeg");
    }
}
