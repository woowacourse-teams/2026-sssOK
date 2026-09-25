package com.sssok.infrastructure.image;

import static org.assertj.core.api.Assertions.assertThat;

import com.sssok.application.port.out.ImageProcessorPort.CaptureInfo;
import com.sssok.application.port.out.ImageProcessorPort.DerivativeSpec;
import com.sssok.application.port.out.ImageProcessorPort.DerivedImages;
import com.sssok.domain.file.DerivativeFormat;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.junit.jupiter.api.Test;

// EXIF 추출은 라이브러리에 맡기지만, 없는 사진에서 터지지 않는지와 좌표 변환은 우리 책임이다.
class ThumbnailatorImageProcessorTest {

    private static final DerivativeSpec THUMBNAIL =
        new DerivativeSpec(400, DerivativeFormat.WEBP, 0.80f);
    private static final DerivativeSpec PREVIEW =
        new DerivativeSpec(1600, DerivativeFormat.WEBP, 0.85f);

    private final ThumbnailatorImageProcessor processor = new ThumbnailatorImageProcessor();

    // ImageIO 로 만든 이미지에는 EXIF 가 없다. 실제 업로드의 상당수가 이렇다.
    @Test
    void EXIF가_없는_이미지는_빈_값을_돌려준다() {
        CaptureInfo capture = processor.readCaptureInfo(plainJpeg());

        assertThat(capture.takenAt()).isNull();
        assertThat(capture.location()).isNull();
    }

    // 사진 한 장이 깨졌다고 배치가 멈추면 안 된다.
    @Test
    void 이미지가_아니면_예외를_던지지_않는다() {
        CaptureInfo capture = processor.readCaptureInfo("이건 이미지가 아니다".getBytes());

        assertThat(capture.takenAt()).isNull();
        assertThat(capture.location()).isNull();
    }

    @Test
    void EXIF에_기록된_촬영_시각과_좌표를_읽는다() {
        CaptureInfo capture = processor.readCaptureInfo(jpegWithExif());

        assertThat(capture.takenAt()).isEqualTo(Instant.parse("2026-08-01T12:30:00Z"));
        assertThat(capture.location()).isNotNull();
        assertThat(capture.location().latitude())
            .isEqualByComparingTo(new BigDecimal("37.566500"));
        assertThat(capture.location().longitude())
            .isEqualByComparingTo(new BigDecimal("126.978000"));
    }

    @Test
    void 손상된_이미지는_축소하지_못하고_빈_값이_된다() {
        assertThat(processor.derive("이건 이미지가 아니다".getBytes(), THUMBNAIL, PREVIEW)).isEmpty();
    }

    // 네이티브 WebP 인코더가 실제로 뜨는지까지 본다. JAR 안의 .so/.dylib 를 꺼내 쓰는 구조라
    // 플랫폼이 맞지 않으면 여기서만 드러난다.
    @Test
    void 썸네일과_프리뷰를_WebP로_인코딩한다() throws IOException {
        DerivedImages derived = processor.derive(plainJpeg(3000, 2000), THUMBNAIL, PREVIEW)
            .orElseThrow();

        assertThat(formatOf(derived.thumbnail().content())).isEqualToIgnoringCase("webp");
        assertThat(formatOf(derived.preview().content())).isEqualToIgnoringCase("webp");
    }

    // 클라이언트가 자리를 미리 잡는 데 쓰는 값이라, 파생본이 아니라 원본 크기여야 한다.
    @Test
    void 파생본이_아니라_원본의_크기를_돌려준다() {
        DerivedImages derived = processor.derive(plainJpeg(3000, 2000), THUMBNAIL, PREVIEW)
            .orElseThrow();

        assertThat(derived.sourceWidth()).isEqualTo(3000);
        assertThat(derived.sourceHeight()).isEqualTo(2000);
    }

    @Test
    void 파생본마다_다른_너비로_줄인다() throws IOException {
        DerivedImages derived = processor.derive(plainJpeg(3000, 2000), THUMBNAIL, PREVIEW)
            .orElseThrow();

        assertThat(widthOf(derived.thumbnail().content())).isEqualTo(400);
        assertThat(widthOf(derived.preview().content())).isEqualTo(1600);
    }

    // 확대한 파생본은 원본보다 크면서 더 흐리기만 하다.
    @Test
    void 원본이_목표_너비보다_작으면_늘리지_않는다() throws IOException {
        DerivedImages derived = processor.derive(plainJpeg(300, 200), THUMBNAIL, PREVIEW)
            .orElseThrow();

        assertThat(widthOf(derived.thumbnail().content())).isEqualTo(300);
        assertThat(widthOf(derived.preview().content())).isEqualTo(300);
    }

    // GIF 는 프리뷰를 만들지 않는다. 스펙이 없으면 결과도 비어 있어야, 부르는 쪽이 올릴지
    // 말지를 그 값 하나로 정할 수 있다.
    @Test
    void 프리뷰_스펙이_없으면_썸네일만_만든다() {
        DerivedImages derived = processor.derive(plainJpeg(3000, 2000), THUMBNAIL, null)
            .orElseThrow();

        assertThat(derived.thumbnail()).isNotNull();
        assertThat(derived.hasPreview()).isFalse();
    }

    // WebP 와 달리 JPEG 은 알파를 담지 못한다. 투명한 PNG 를 그대로 넘기면 라이터가 색을 뒤집어
    // 붉게 물든 사진이 나온다.
    @Test
    void 투명한_PNG를_JPEG로_내보내도_깨지지_않는다() throws IOException {
        DerivativeSpec jpegSpec = new DerivativeSpec(400, DerivativeFormat.JPEG, 0.80f);

        DerivedImages derived = processor.derive(transparentPng(), jpegSpec, null).orElseThrow();

        assertThat(formatOf(derived.thumbnail().content())).isEqualToIgnoringCase("jpeg");
    }

    private int widthOf(byte[] image) throws IOException {
        return ImageIO.read(new ByteArrayInputStream(image)).getWidth();
    }

    // 바이트를 직접 열어 실제로 무슨 형식으로 인코딩됐는지 본다. 선언한 Content-Type 만 보면
    // WebP 라고 말하고 JPEG 바이트를 올려도 통과해버린다.
    private String formatOf(byte[] image) throws IOException {
        try (ImageInputStream stream = ImageIO.createImageInputStream(
            new ByteArrayInputStream(image))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
            assertThat(readers).hasNext();
            return readers.next().getFormatName();
        }
    }

    private byte[] plainJpeg(int width, int height) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = image.createGraphics();
            // 단색이면 어떤 품질로 인코딩해도 거의 같은 크기가 나와 비교가 무의미해진다.
            graphics.setPaint(new GradientPaint(0, 0, Color.RED, width, height, Color.BLUE));
            graphics.fillRect(0, 0, width, height);
            graphics.dispose();
            ImageIO.write(image, "jpg", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private byte[] transparentPng() {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(new BufferedImage(200, 150, BufferedImage.TYPE_INT_ARGB), "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private byte[] plainJpeg() {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(new BufferedImage(100, 80, BufferedImage.TYPE_INT_RGB), "jpg", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // EXIF 를 쓰는 라이브러리를 더 들이지 않으려고 APP1 세그먼트를 직접 만들어 끼운다.
    private byte[] jpegWithExif() {
        byte[] exif = ExifFixture.app1Segment(
            "2026:08:01 12:30:00", 37.5665, 126.978);
        byte[] jpeg = plainJpeg();
        byte[] result = new byte[jpeg.length + exif.length];
        // SOI(2바이트) 바로 뒤에 APP1 을 넣는 것이 JPEG 규격이다.
        System.arraycopy(jpeg, 0, result, 0, 2);
        System.arraycopy(exif, 0, result, 2, exif.length);
        System.arraycopy(jpeg, 2, result, 2 + exif.length, jpeg.length - 2);
        return result;
    }
}
