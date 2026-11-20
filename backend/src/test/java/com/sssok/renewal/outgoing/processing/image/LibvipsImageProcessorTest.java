package com.sssok.renewal.outgoing.processing.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sssok.domain.file.DerivativeFormat;
import com.sssok.renewal.outgoing.processing.image.ThumbnailatorImageProcessor.DerivativeSpec;
import java.util.Optional;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import java.io.ByteArrayInputStream;

// 같은 계약 테스트를 실제 JNI에 적용한다. 전용 Docker 검증에서만 실행한다.
@Tag("native-image")
class LibvipsImageProcessorTest extends ThumbnailatorImageProcessorTest {
    @Override
    protected ThumbnailatorImageProcessor createProcessor() {
        return new ThumbnailatorImageProcessor(new ImageDerivativeOperations(),
            Optional.of(new LibvipsImageProcessor()));
    }

    @ParameterizedTest
    @CsvSource({"transparent.webp,96,64", "landscape.heic,96,64", "rotated.heif,64,96"})
    void 새_형식은_네이티브로_회전과_색상을_보존하며_파생본을_만든다(String name, int width, int height)
            throws Exception {
        byte[] source;
        try (var input = getClass().getResourceAsStream("/images/upload-formats/" + name)) {
            assertThat(input).isNotNull();
            source = input.readAllBytes();
        }
        var spec = new DerivativeSpec(32, DerivativeFormat.WEBP, .9f);
        assertThat(new LibvipsImageProcessor().supports(source, spec, spec)).isTrue();
        var result = createProcessor().derive(source, spec,
            new DerivativeSpec(48, DerivativeFormat.WEBP, .9f)).orElseThrow();
        assertThat(result.sourceWidth()).isEqualTo(width);
        assertThat(result.sourceHeight()).isEqualTo(height);
        BufferedImage thumb = ImageIO.read(new ByteArrayInputStream(result.thumbnail().content()));
        BufferedImage preview = ImageIO.read(new ByteArrayInputStream(result.preview().content()));
        assertThat(thumb.getWidth()).isEqualTo(32);
        assertThat(preview.getWidth()).isEqualTo(48);
        assertThat(preview.getHeight()).isEqualTo((int) Math.round(height * 48.0 / width));
        // 좌측의 빨강은 시계 방향 회전 뒤에는 상단에 위치한다.
        var red = new java.awt.Color(preview.getRGB(preview.getWidth() / 4, preview.getHeight() / 4), true);
        assertThat(red.getRed()).isGreaterThan(180);
        assertThat(red.getBlue()).isLessThan(80);
        if (name.endsWith("webp")) {
            assertThat(preview.getColorModel().hasAlpha()).isTrue();
            assertThat((preview.getRGB(40, 26) >>> 24) & 255).isLessThan(20);
        }
        assertThat(createProcessor().derive(source, spec, null).orElseThrow().hasPreview()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"transparent.webp", "landscape.heic", "rotated.heif"})
    void 잘린_새_형식은_파생본을_만들지_않고_다음_정상_요청은_처리한다(String name) throws Exception {
        byte[] source;
        try (var input = getClass().getResourceAsStream("/images/upload-formats/" + name)) {
            source = input.readAllBytes();
        }
        var processor = createProcessor();
        var spec = new DerivativeSpec(32, DerivativeFormat.WEBP, .8f);
        assertThatThrownBy(() -> processor.derive(java.util.Arrays.copyOf(source, 64), spec, null))
            .isInstanceOf(RuntimeException.class);
        assertThat(processor.derive(source, spec, null)).isPresent();
    }

    @Test
    void 검증하지_않은_입력과_출력은_기존_처리기를_사용한다() {
        LibvipsImageProcessor processor = new LibvipsImageProcessor();
        DerivativeSpec webp = new DerivativeSpec(400, DerivativeFormat.WEBP, .8f);
        DerivativeSpec jpeg = new DerivativeSpec(400, DerivativeFormat.JPEG, .8f);
        byte[] jpegHeader = {(byte) 255, (byte) 216, (byte) 255};
        assertThat(processor.supports(jpegHeader, webp, null)).isTrue();
        assertThat(processor.supports(jpegHeader, jpeg, null)).isFalse();
        assertThat(processor.supports(new byte[] {1, 2, 3}, webp, null)).isFalse();
    }

    @Test
    void 네이티브_실패_후에도_다음_호출은_처리할_수_있다() throws Exception {
        var processor = createProcessor();
        DerivativeSpec webp = new DerivativeSpec(400, DerivativeFormat.WEBP, .8f);
        assertThatThrownBy(() -> processor.derive(new byte[] {(byte) 255, (byte) 216, (byte) 255}, webp, null))
            .isInstanceOf(IllegalStateException.class);
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(100, 80, BufferedImage.TYPE_INT_RGB), "jpeg", bytes);
        assertThat(processor.derive(bytes.toByteArray(), webp, null).orElseThrow().sourceWidth()).isEqualTo(100);
    }

    @Test
    void 큰_픽셀_수를_선언한_JPEG는_디코딩_전에_거절한다() throws Exception {
        var output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(100, 80, BufferedImage.TYPE_INT_RGB), "jpeg", output);
        byte[] source = output.toByteArray();
        boolean patched = false;
        for (int i = 0; i + 8 < source.length; i++) {
            if ((source[i] & 255) == 255 && (source[i + 1] & 255) == 192) {
                source[i + 5] = source[i + 7] = (byte) (20000 >> 8);
                source[i + 6] = source[i + 8] = (byte) 20000;
                patched = true;
                break;
            }
        }
        assertThat(patched).isTrue();
        var processor = createProcessor();
        var spec = new DerivativeSpec(400, DerivativeFormat.WEBP, .8f);
        assertThatThrownBy(() -> processor.derive(source, spec, null))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("dimensions");
        assertThat(processor.derive(output.toByteArray(), spec, null).orElseThrow().sourceWidth())
            .isEqualTo(100);
    }

    @Test
    void 설정한_WebP_품질이_네이티브_인코더에_전달된다() throws Exception {
        var image = new BufferedImage(200, 150, BufferedImage.TYPE_INT_RGB);
        var random = new java.util.Random(557);
        for (int y = 0; y < 150; y++) {
            for (int x = 0; x < 200; x++) image.setRGB(x, y, random.nextInt(0x1000000));
        }
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "jpeg", bytes);
        var processor = createProcessor();
        var low = processor.derive(bytes.toByteArray(), new DerivativeSpec(200, DerivativeFormat.WEBP, .1f), null).orElseThrow();
        var high = processor.derive(bytes.toByteArray(), new DerivativeSpec(200, DerivativeFormat.WEBP, .95f), null).orElseThrow();
        assertThat(high.thumbnail().content().length).isGreaterThan(low.thumbnail().content().length);
    }
}
