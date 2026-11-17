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

// 같은 계약 테스트를 실제 FFM에 적용한다. 전용 Docker 검증에서만 실행한다.
@Tag("native-image")
class LibvipsFfmImageProcessorTest extends ThumbnailatorImageProcessorTest {
    @Override
    protected ThumbnailatorImageProcessor createProcessor() {
        return new ThumbnailatorImageProcessor(new ImageDerivativeOperations(),
            Optional.of(new LibvipsFfmImageProcessor()));
    }

    @Test
    void 검증하지_않은_입력과_출력은_기존_처리기를_사용한다() {
        LibvipsFfmImageProcessor processor = new LibvipsFfmImageProcessor();
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
            .isInstanceOf(RuntimeException.class);
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
