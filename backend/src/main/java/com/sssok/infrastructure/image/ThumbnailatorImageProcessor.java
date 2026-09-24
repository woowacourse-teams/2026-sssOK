package com.sssok.infrastructure.image;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.lang.GeoLocation;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.exif.GpsDirectory;
import com.luciad.imageio.webp.WebPWriteParam;
import com.sssok.application.port.out.ImageProcessorPort;
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
import java.util.Optional;
import java.util.TimeZone;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.stereotype.Component;

@Component
public class ThumbnailatorImageProcessor implements ImageProcessorPort {

    // 소수점 6자리면 약 10cm 단위다. 사진이 찍힌 자리를 가리키기에 충분하고,
    // 컬럼 정의(NUMERIC(9,6))와도 맞춘다.
    private static final int COORDINATE_SCALE = 6;

    // webp-imageio 가 등록하는 압축 방식 이름. 라이브러리가 문자열로만 받아 상수로 묶어 둔다.
    private static final String WEBP_LOSSY = "Lossy";

    @Override
    public Optional<DerivedImages> derive(byte[] source, DerivativeSpec thumbnail,
                                          DerivativeSpec preview) {
        try {
            // 원본 디코딩은 한 번만 한다. 여기가 파생본 생성 시간의 대부분이라, 썸네일과 프리뷰를
            // 따로 만들면 워커 점유 시간이 곱절이 된다.
            BufferedImage original = ImageIO.read(new ByteArrayInputStream(source));
            // ImageIO 는 읽을 수 없는 형식이면 예외 대신 null 을 준다.
            if (original == null) {
                return Optional.empty();
            }
            return Optional.of(new DerivedImages(
                original.getWidth(),
                original.getHeight(),
                derive(original, thumbnail),
                preview == null ? null : derive(original, preview)));
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

    // 원본이 이미 작으면 늘리지 않는다. 확대한 파생본은 원본보다 크면서 더 흐리다.
    private DerivedImage derive(BufferedImage original, DerivativeSpec spec) throws IOException {
        int width = Math.min(spec.maxWidth(), original.getWidth());
        BufferedImage scaled = Thumbnails.of(original)
            .width(width)
            .keepAspectRatio(true)
            .asBufferedImage();
        return new DerivedImage(encode(scaled, spec.format(), spec.quality()), spec.format());
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
