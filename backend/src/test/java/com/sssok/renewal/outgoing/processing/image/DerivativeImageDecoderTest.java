package com.sssok.renewal.outgoing.processing.image;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class DerivativeImageDecoderTest {
    private final DerivativeImageDecoder decoder = new DerivativeImageDecoder();

    @Test
    void 고해상도_JPEG의_디코딩_픽셀을_줄이고_원본_치수는_보존한다() throws IOException {
        var result = decoder.decode(image(3213, 5712, "jpg"), ExifOrientation.NORMAL, 1600);

        assertThat(result.sourceWidth()).isEqualTo(3213);
        assertThat(result.sourceHeight()).isEqualTo(5712);
        assertThat(result.pixels().getWidth()).isEqualTo(1607);
        assertThat(result.pixels().getHeight()).isEqualTo(2856);
    }

    @Test
    void 회전되는_사진은_표시_너비를_기준으로_읽는다() throws IOException {
        var result = decoder.decode(image(1200, 800, "jpg"), ExifOrientation.ROTATE_90_CLOCKWISE, 600);

        assertThat(result.sourceWidth()).isEqualTo(800);
        assertThat(result.sourceHeight()).isEqualTo(1200);
        // 저장된 가로 1200 기준으로 2배 샘플링하면 표시 너비가 400으로 부족해진다.
        assertThat(result.pixels().getWidth()).isEqualTo(1200);
        assertThat(result.pixels().getHeight()).isEqualTo(800);
    }

    @Test
    void 작은_JPEG는_그대로_읽는다() throws IOException {
        var result = decoder.decode(image(300, 200, "jpg"), ExifOrientation.NORMAL, 1600);
        assertThat(result.pixels().getWidth()).isEqualTo(300);
        assertThat(result.pixels().getHeight()).isEqualTo(200);
    }

    @Test
    void PNG는_원래_해상도를_유지한다() throws IOException {
        var result = decoder.decode(image(1200, 800, "png"), ExifOrientation.NORMAL, 400);
        assertThat(result.pixels().getWidth()).isEqualTo(1200);
        assertThat(result.pixels().getHeight()).isEqualTo(800);
    }

    @Test
    void 읽을_수_없는_입력은_빈_결과다() {
        assertThat(decoder.decode(new byte[] {1, 2, 3}, ExifOrientation.NORMAL, 400)).isNull();
    }

    private byte[] image(int width, int height, String format) throws IOException {
        var out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR), format, out);
        return out.toByteArray();
    }
}
