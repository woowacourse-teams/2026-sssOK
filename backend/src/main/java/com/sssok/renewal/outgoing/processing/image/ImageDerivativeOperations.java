package com.sssok.renewal.outgoing.processing.image;

import com.sssok.renewal.outgoing.processing.image.ThumbnailatorImageProcessor.*;
import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.lang.GeoLocation;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.exif.GpsDirectory;
import com.luciad.imageio.webp.WebPWriteParam;
import com.sssok.domain.file.DerivativeFormat;
import com.sssok.domain.file.GeoPoint;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.stereotype.Component;

@Component
public class ImageDerivativeOperations {
    // 소수점 6자리면 약 10cm 단위다. 사진이 찍힌 자리를 가리키기에 충분하고,
    // 컬럼 정의(NUMERIC(9,6))와도 맞춘다.
    private static final int COORDINATE_SCALE = 6;

    // webp-imageio 가 등록하는 압축 방식 이름. 라이브러리가 문자열로만 받아 상수로 묶어 둔다.
    private static final String WEBP_LOSSY = "Lossy";

    public DerivativeImageDecoder.DecodedImage decode(byte[] source, ExifOrientation orientation, int requiredWidth) {
        return new DerivativeImageDecoder().decode(source, orientation, requiredWidth);
    }

    public ExifOrientation readOrientation(byte[] source) {
        try {
            Metadata metadata = ImageMetadataReader.readMetadata(new ByteArrayInputStream(source));
            ExifIFD0Directory exif = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
            Integer value = exif == null
                ? null
                : exif.getInteger(ExifIFD0Directory.TAG_ORIENTATION);
            return ExifOrientation.from(value);
        } catch (ImageProcessingException | IOException | RuntimeException e) {
            return ExifOrientation.NORMAL;
        }
    }

    // EXIF 는 있는 사진도 있고 없는 사진도 있다. 없다고 오류가 아니라, 못 읽으면 비워서 돌려준다.
    public CaptureInfo readCaptureInfo(byte[] source) {
        try {
            Metadata metadata = ImageMetadataReader.readMetadata(new ByteArrayInputStream(source));
            return new CaptureInfo(takenAt(metadata), location(metadata));
        } catch (ImageProcessingException | IOException | RuntimeException e) {
            return CaptureInfo.empty();
        }
    }

    // EXIF 의 촬영 시각에는 시간대가 없다. 어느 지역에서 찍혔는지 알 수 없으므로 UTC 로 읽는다 —
    // 시스템 시간대로 읽으면 서버를 옮길 때 같은 사진의 시각이 달라진다.
    private Instant takenAt(Metadata metadata) {
        ExifSubIFDDirectory exif = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
        if (exif == null) {
            return null;
        }
        Date original = exif.getDate(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL,
            TimeZone.getTimeZone(ZoneOffset.UTC));
        return original == null ? null : original.toInstant();
    }

    private GeoPoint location(Metadata metadata) {
        GpsDirectory gps = metadata.getFirstDirectoryOfType(GpsDirectory.class);
        if (gps == null || gps.getGeoLocation() == null || gps.getGeoLocation().isZero()) {
            return null;
        }
        GeoLocation found = gps.getGeoLocation();
        return GeoPoint.ofNullable(
            BigDecimal.valueOf(found.getLatitude()).setScale(COORDINATE_SCALE, RoundingMode.HALF_UP),
            BigDecimal.valueOf(found.getLongitude()).setScale(COORDINATE_SCALE, RoundingMode.HALF_UP));
    }

    // 원본 전체를 먼저 회전하면 디코딩 원본과 같은 크기의 회전 버퍼가 동시에 살아 있어 메모리를
    // 크게 쓴다. 표시 방향의 너비를 기준으로 먼저 줄인 뒤 작은 이미지에만 방향을 적용한다.
    public BufferedImage resizeAndOrient(BufferedImage source, ExifOrientation orientation,
                                           int maxWidth, SourceSize sourceSize)
        throws IOException {
        // 샘플링·프리뷰 축소 과정에서 생긴 반올림을 다시 비율 계산에 쓰지 않는다.
        // 최종 크기는 항상 원본의 표시 치수로 계산한다.
        int displayWidth = Math.min(maxWidth, sourceSize.width());
        int displayHeight = Math.max(1, (int) Math.round(
            sourceSize.height() * ((double) displayWidth / sourceSize.width())));
        int width = orientation.displayWidthOf(displayWidth, displayHeight);
        int height = orientation.displayHeightOf(displayWidth, displayHeight);
        BufferedImage scaled = source;
        if (source.getWidth() != width || source.getHeight() != height) {
            scaled = Thumbnails.of(source).forceSize(width, height).asBufferedImage();
        }
        return orientation.applyTo(scaled);
    }

    // EXIF 방향을 반영한 원본 표시 치수. 디코딩 픽셀과 별도로 전달한다.
    public record SourceSize(int width, int height) {
    }

    public DerivedImage encodeDerivative(BufferedImage image, DerivativeSpec spec) throws IOException {
        return new DerivedImage(encode(image, spec.format(), spec.quality()), spec.format());
    }

    // Thumbnailator 의 outputFormat 을 쓰지 않고 직접 인코딩한다. WebP 라이터는 ImageIO 에
    // 얹힌 JNI 플러그인이고, 품질을 주려면 전용 WebPWriteParam 이 필요해서다.
    private byte[] encode(BufferedImage image, DerivativeFormat format, float quality)
        throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByMIMEType(format.contentType()).next();
        ImageWriteParam param = format == DerivativeFormat.WEBP
            ? new WebPWriteParam(Locale.getDefault())
            : writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        if (format == DerivativeFormat.WEBP) {
            // 무손실 모드도 있지만 사진에서는 파일이 몇 배로 커진다.
            param.setCompressionType(WEBP_LOSSY);
        }
        param.setCompressionQuality(quality);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ImageOutputStream imageOut = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(imageOut);
            writer.write(null, new IIOImage(withoutAlpha(image, format), null, null), param);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }

    // JPEG 은 알파 채널을 담지 못한다. 투명한 PNG 를 그대로 넘기면 라이터가 색을 뒤집어
    // 붉게 물든 사진이 나온다. WebP 는 알파를 담을 수 있어 건드리지 않는다.
    private BufferedImage withoutAlpha(BufferedImage image, DerivativeFormat format) {
        if (format != DerivativeFormat.JPEG || !image.getColorModel().hasAlpha()) {
            return image;
        }
        BufferedImage opaque = new BufferedImage(
            image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = opaque.createGraphics();
        // 투명한 자리는 검정이 아니라 흰색으로 채운다. 검정으로 두면 배경이 투명한 로고가
        // 까맣게 뭉개져 무엇인지 알아볼 수 없다.
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        graphics.drawImage(image, 0, 0, null);
        graphics.dispose();
        return opaque;
    }

}
