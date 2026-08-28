package com.sssok.infrastructure.image;

import static org.assertj.core.api.Assertions.assertThat;

import com.sssok.application.port.out.ImageProcessorPort.CaptureInfo;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.Instant;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

// EXIF 추출은 라이브러리에 맡기지만, 없는 사진에서 터지지 않는지와 좌표 변환은 우리 책임이다.
class ThumbnailatorImageProcessorTest {

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
        assertThat(processor.shrink("이건 이미지가 아니다".getBytes(), 400, "jpg")).isEmpty();
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
