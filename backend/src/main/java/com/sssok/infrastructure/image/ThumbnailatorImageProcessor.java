package com.sssok.infrastructure.image;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.lang.GeoLocation;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.exif.GpsDirectory;
import com.sssok.application.port.out.ImageProcessorPort;
import com.sssok.domain.file.GeoPoint;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.Optional;
import java.util.TimeZone;
import javax.imageio.ImageIO;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.stereotype.Component;

@Component
public class ThumbnailatorImageProcessor implements ImageProcessorPort {

    // 소수점 6자리면 약 10cm 단위다. 사진이 찍힌 자리를 가리키기에 충분하고,
    // 컬럼 정의(NUMERIC(9,6))와도 맞춘다.
    private static final int COORDINATE_SCALE = 6;

    @Override
    public Optional<ProcessedImage> shrink(byte[] source, int maxWidth, String format) {
        try {
            BufferedImage original = ImageIO.read(new ByteArrayInputStream(source));
            // ImageIO 는 읽을 수 없는 형식이면 예외 대신 null 을 준다.
            if (original == null) {
                return Optional.empty();
            }
            return Optional.of(new ProcessedImage(
                original.getWidth(),
                original.getHeight(),
                toThumbnail(original, maxWidth, format)));
        } catch (IOException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    // EXIF 는 있는 사진도 있고 없는 사진도 있다. 없다고 오류가 아니라, 못 읽으면 비워서 돌려준다.
    @Override
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

    // 원본이 이미 작으면 늘리지 않는다. 확대한 썸네일은 원본보다 크면서 더 흐리다.
    private byte[] toThumbnail(BufferedImage original, int maxWidth, String format)
        throws IOException {
        int width = Math.min(maxWidth, original.getWidth());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Thumbnails.of(original)
            .width(width)
            .keepAspectRatio(true)
            .outputFormat(format)
            .toOutputStream(out);
        return out.toByteArray();
    }
}
