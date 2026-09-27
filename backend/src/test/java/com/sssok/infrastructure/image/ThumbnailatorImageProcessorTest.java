package com.sssok.infrastructure.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

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
import java.util.Map;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

// EXIF 추출은 라이브러리에 맡기지만, 없는 사진에서 터지지 않는지와 좌표 변환은 우리 책임이다.
class ThumbnailatorImageProcessorTest {

    private static final Map<String, Color> COLORS = Map.of(
        "RED", Color.RED,
        "GREEN", Color.GREEN,
        "BLUE", Color.BLUE,
        "YELLOW", Color.YELLOW);

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

    @ParameterizedTest
    @CsvSource({
        "1, 120, 80, RED, GREEN, BLUE, YELLOW",
        "2, 120, 80, GREEN, RED, YELLOW, BLUE",
        "3, 120, 80, YELLOW, BLUE, GREEN, RED",
        "4, 120, 80, BLUE, YELLOW, RED, GREEN",
        "5, 80, 120, RED, BLUE, GREEN, YELLOW",
        "6, 80, 120, BLUE, RED, YELLOW, GREEN",
        "7, 80, 120, YELLOW, GREEN, BLUE, RED",
        "8, 80, 120, GREEN, YELLOW, RED, BLUE"
    })
    void EXIF_Orientation을_파생본_픽셀과_크기에_반영한다(
        int orientation, int expectedWidth, int expectedHeight,
        String topLeft, String topRight, String bottomLeft, String bottomRight) throws IOException {
        DerivedImages derived = processor.derive(
            jpegWithOrientation(orientation), THUMBNAIL, null).orElseThrow();
        byte[] image = derived.thumbnail().content();

        assertThat(derived.sourceWidth()).isEqualTo(expectedWidth);
        assertThat(derived.sourceHeight()).isEqualTo(expectedHeight);
        assertThat(widthOf(image)).isEqualTo(expectedWidth);
        assertThat(heightOf(image)).isEqualTo(expectedHeight);
        assertColorNear(colorAt(image, 10, 10), color(topLeft));
        assertColorNear(colorAt(image, expectedWidth - 11, 10), color(topRight));
        assertColorNear(colorAt(image, 10, expectedHeight - 11), color(bottomLeft));
        assertColorNear(colorAt(image, expectedWidth - 11, expectedHeight - 11), color(bottomRight));
    }

    @Test
    void 회전한_이미지에서_썸네일과_프리뷰를_각각의_너비로_축소한다() throws IOException {
        DerivativeSpec thumbnail = new DerivativeSpec(200, DerivativeFormat.WEBP, 0.80f);
        DerivativeSpec preview = new DerivativeSpec(600, DerivativeFormat.WEBP, 0.85f);

        DerivedImages derived = processor.derive(
            jpegWithOrientation(6, 1200, 800), thumbnail, preview).orElseThrow();

        assertThat(derived.sourceWidth()).isEqualTo(800);
        assertThat(derived.sourceHeight()).isEqualTo(1200);
        assertThat(widthOf(derived.thumbnail().content())).isEqualTo(200);
        assertThat(heightOf(derived.thumbnail().content())).isEqualTo(300);
        assertThat(widthOf(derived.preview().content())).isEqualTo(600);
        assertThat(heightOf(derived.preview().content())).isEqualTo(900);
    }

    @Test
    void EXIF_Orientation이_없으면_픽셀_방향을_유지한다() throws IOException {
        DerivedImages derived = processor.derive(quadrantJpeg(120, 80), THUMBNAIL, null)
            .orElseThrow();
        byte[] image = derived.thumbnail().content();

        assertColorNear(colorAt(image, 10, 10), Color.RED);
        assertColorNear(colorAt(image, 109, 10), Color.GREEN);
        assertColorNear(colorAt(image, 10, 69), Color.BLUE);
        assertColorNear(colorAt(image, 109, 69), Color.YELLOW);
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
    // 붉게 물든 사진이 나오고, 검정으로 합성하면 배경이 투명한 로고가 까맣게 뭉개진다.
    // 그래서 형식만 보지 않고 투명했던 자리의 픽셀이 실제로 흰색인지까지 본다.
    @Test
    void 투명한_PNG를_JPEG로_내보내면_투명한_자리가_흰색이_된다() throws IOException {
        DerivativeSpec jpegSpec = new DerivativeSpec(400, DerivativeFormat.JPEG, 0.80f);

        DerivedImages derived = processor.derive(halfTransparentPng(), jpegSpec, null)
            .orElseThrow();

        assertThat(formatOf(derived.thumbnail().content())).isEqualToIgnoringCase("jpeg");
        // 왼쪽 절반이 투명했던 자리다. 경계에서는 JPEG 블록 잡음이 섞이므로 안쪽을 집는다.
        assertColorNear(colorAt(derived.thumbnail().content(), 20, 20), Color.WHITE);
        // 불투명했던 자리는 그대로 남아야 한다 — 전체를 흰색으로 덮어도 위 단정은 통과하므로.
        assertColorNear(colorAt(derived.thumbnail().content(), 180, 20), Color.RED);
    }

    // 빈 값은 "다시 태워도 결과가 같다" 는 뜻이고, 부르는 쪽은 그때만 영구 FAILED 로 확정한다.
    // 디코딩을 넘긴 뒤의 실패까지 빈 값으로 섞으면 멀쩡한 사진이 인코더의 한 번 흔들림으로 사라진다.
    @Test
    void 디코딩_이후의_인코딩_실패는_빈_값이_아니라_예외가_된다() {
        DerivativeSpec 인코더가_거부하는_품질 = new DerivativeSpec(400, DerivativeFormat.WEBP, 2.0f);

        assertThatThrownBy(() ->
            processor.derive(plainJpeg(300, 200), 인코더가_거부하는_품질, null))
            .isInstanceOf(RuntimeException.class);
    }

    private int widthOf(byte[] image) throws IOException {
        return ImageIO.read(new ByteArrayInputStream(image)).getWidth();
    }

    private int heightOf(byte[] image) throws IOException {
        return ImageIO.read(new ByteArrayInputStream(image)).getHeight();
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

    // 왼쪽 절반은 투명하고 오른쪽 절반은 불투명하다. 전체가 투명하면 흰색으로 덮어버리는
    // 구현도 통과해버려서, 지켜야 할 것(투명한 자리만 흰색)을 가리지 못한다.
    private byte[] halfTransparentPng() {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            BufferedImage image = new BufferedImage(200, 150, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = image.createGraphics();
            graphics.setColor(Color.RED);
            graphics.fillRect(100, 0, 100, 150);
            graphics.dispose();
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // JPEG 은 손실 압축이라 흰색을 넣어도 254 로 돌아온다. 검정(0)·붉은 왜곡과는 한참 떨어져
    // 있으므로, 이 정도 여유로도 지키려는 것(무엇으로 합성했는지)은 그대로 갈린다.
    private void assertColorNear(Color actual, Color expected) {
        int tolerance = 8;
        assertThat(actual.getRed()).isCloseTo(expected.getRed(), within(tolerance));
        assertThat(actual.getGreen()).isCloseTo(expected.getGreen(), within(tolerance));
        assertThat(actual.getBlue()).isCloseTo(expected.getBlue(), within(tolerance));
    }

    private Color colorAt(byte[] image, int x, int y) throws IOException {
        return new Color(ImageIO.read(new ByteArrayInputStream(image)).getRGB(x, y));
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

    private byte[] jpegWithOrientation(int orientation) {
        return jpegWithOrientation(orientation, 120, 80);
    }

    private byte[] jpegWithOrientation(int orientation, int width, int height) {
        return withApp1(quadrantJpeg(width, height),
            ExifFixture.orientationApp1Segment(orientation));
    }

    private byte[] quadrantJpeg(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.RED);
        graphics.fillRect(0, 0, width / 2, height / 2);
        graphics.setColor(Color.GREEN);
        graphics.fillRect(width / 2, 0, width - width / 2, height / 2);
        graphics.setColor(Color.BLUE);
        graphics.fillRect(0, height / 2, width / 2, height - height / 2);
        graphics.setColor(Color.YELLOW);
        graphics.fillRect(width / 2, height / 2, width - width / 2, height - height / 2);
        graphics.dispose();

        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "jpg", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Color color(String name) {
        return COLORS.get(name);
    }

    // EXIF 를 쓰는 라이브러리를 더 들이지 않으려고 APP1 세그먼트를 직접 만들어 끼운다.
    private byte[] jpegWithExif() {
        byte[] exif = ExifFixture.app1Segment(
            "2026:08:01 12:30:00", 37.5665, 126.978);
        return withApp1(plainJpeg(), exif);
    }

    private byte[] withApp1(byte[] jpeg, byte[] exif) {
        byte[] result = new byte[jpeg.length + exif.length];
        // SOI(2바이트) 바로 뒤에 APP1 을 넣는 것이 JPEG 규격이다.
        System.arraycopy(jpeg, 0, result, 0, 2);
        System.arraycopy(exif, 0, result, 2, exif.length);
        System.arraycopy(jpeg, 2, result, 2 + exif.length, jpeg.length - 2);
        return result;
    }
}
