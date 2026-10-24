package com.sssok.renewal.outgoing.processing.image;

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
import java.io.UncheckedIOException;
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
public class ThumbnailatorImageProcessor {

    // 소수점 6자리면 약 10cm 단위다. 사진이 찍힌 자리를 가리키기에 충분하고,
    // 컬럼 정의(NUMERIC(9,6))와도 맞춘다.
    private static final int COORDINATE_SCALE = 6;

    // webp-imageio 가 등록하는 압축 방식 이름. 라이브러리가 문자열로만 받아 상수로 묶어 둔다.
    private static final String WEBP_LOSSY = "Lossy";

    public Optional<DerivedImages> derive(byte[] source, DerivativeSpec thumbnail,
                                          DerivativeSpec preview) {
        ExifOrientation orientation = readOrientation(source);
        int requiredWidth = preview == null ? thumbnail.maxWidth()
            : Math.max(thumbnail.maxWidth(), preview.maxWidth());
        var decoded = new DerivativeImageDecoder().decode(source, orientation, requiredWidth);
        if (decoded == null) {
            return Optional.empty();
        }
        BufferedImage original = decoded.pixels();
        try {
            if (preview != null && preview.maxWidth() >= thumbnail.maxWidth()) {
                // 압축된 프리뷰를 다시 읽지 않고, 인코딩 전 픽셀을 공유한다.
                // 방향은 프리뷰 생성 시 한 번만 적용한다.
                BufferedImage previewImage = resizeAndOrient(original, orientation, preview.maxWidth(), decoded);
                BufferedImage thumbnailImage = resizeAndOrient(
                    previewImage, ExifOrientation.NORMAL, thumbnail.maxWidth(), decoded);
                return Optional.of(new DerivedImages(
                    decoded.sourceWidth(),
                    decoded.sourceHeight(),
                    encodeDerivative(thumbnailImage, thumbnail),
                    encodeDerivative(previewImage, preview)));
            }
            // 프리뷰가 없거나 더 작게 설정되면 썸네일의 해상도를 낮추지 않는다.
            return Optional.of(new DerivedImages(
                decoded.sourceWidth(),
                decoded.sourceHeight(),
                encodeDerivative(resizeAndOrient(original, orientation, thumbnail.maxWidth(), decoded), thumbnail),
                preview == null ? null : encodeDerivative(
                    resizeAndOrient(original, orientation, preview.maxWidth(), decoded), preview)));
        } catch (IOException e) {
            // 디코딩을 넘긴 뒤의 축소·인코딩 실패는 원본이 깨졌다는 근거가 되지 못한다. 네이티브
            // WebP 라이터가 한 번 흔들린 것을 빈 값으로 돌려주면 멀쩡한 사진이 영구 FAILED 가
            // 되므로, 밖으로 내보내 PROCESSING 으로 남기고 회수 배치가 다시 태우게 한다.
            throw new UncheckedIOException(e);
        }
    }

    private ExifOrientation readOrientation(byte[] source) {
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
    private BufferedImage resizeAndOrient(BufferedImage source, ExifOrientation orientation,
                                           int maxWidth, DerivativeImageDecoder.DecodedImage decoded)
        throws IOException {
        // 샘플링·프리뷰 축소 과정에서 생긴 반올림을 다시 비율 계산에 쓰지 않는다.
        // 최종 크기는 항상 원본의 표시 치수로 계산한다.
        int displayWidth = Math.min(maxWidth, decoded.sourceWidth());
        int displayHeight = Math.max(1, (int) Math.round(
            decoded.sourceHeight() * ((double) displayWidth / decoded.sourceWidth())));
        int width = orientation.displayWidthOf(displayWidth, displayHeight);
        int height = orientation.displayHeightOf(displayWidth, displayHeight);
        BufferedImage scaled = source;
        if (source.getWidth() != width || source.getHeight() != height) {
            scaled = Thumbnails.of(source).forceSize(width, height).asBufferedImage();
        }
        return orientation.applyTo(scaled);
    }

    private DerivedImage encodeDerivative(BufferedImage image, DerivativeSpec spec) throws IOException {
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

    // 파생본 하나를 어떻게 만들지. 값은 application.yml 의 media.image 가 갖는다.
    public record DerivativeSpec(int maxWidth, DerivativeFormat format, float quality) {
    }

    public record DerivedImage(byte[] content, DerivativeFormat format) {
    }

    // 원본 크기는 클라이언트가 자리를 미리 잡는 데 쓰므로 파생본 크기가 아니라 원본의 것을 담는다.
    public record DerivedImages(int sourceWidth, int sourceHeight,
                         DerivedImage thumbnail, DerivedImage preview) {

        public boolean hasPreview() {
            return preview != null;
        }
    }

    public record CaptureInfo(Instant takenAt, GeoPoint location) {

        public static CaptureInfo empty() {
            return new CaptureInfo(null, null);
        }
    }
}
