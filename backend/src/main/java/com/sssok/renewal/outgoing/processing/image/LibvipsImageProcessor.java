package com.sssok.renewal.outgoing.processing.image;

import com.sssok.domain.file.DerivativeFormat;
import com.sssok.renewal.outgoing.processing.image.ThumbnailatorImageProcessor.DerivativeSpec;
import com.sssok.renewal.outgoing.processing.image.ThumbnailatorImageProcessor.DerivedImage;
import com.sssok.renewal.outgoing.processing.image.ThumbnailatorImageProcessor.DerivedImages;
import com.sssok.renewal.outgoing.monitoring.UploadTracing;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** JPEG 최적화 경로와 WebP·HEIF의 네이티브 디코딩을 제공한다. PNG·GIF는 기존 구현이 담당한다. */
@Component
@ConditionalOnProperty(name = "media.image.processor", havingValue = "libvips")
public class LibvipsImageProcessor implements ImageDerivativeEngine {
    private final NativeImageDiagnostics diagnostics;

    public LibvipsImageProcessor() {
        this(null, false);
    }

    @Autowired
    public LibvipsImageProcessor(UploadTracing tracing,
            @Value("${media.image.native-stage-diagnostics:false}") boolean stageDiagnostics) {
        this.diagnostics = new NativeImageDiagnostics(tracing, stageDiagnostics);
        Native.initialize();
    }

    public boolean supports(byte[] source, DerivativeSpec thumbnail, DerivativeSpec preview) {
        return (isJpeg(source) || isWebp(source) || isHeif(source))
            && thumbnail.format() == DerivativeFormat.WEBP
            && (preview == null || preview.format() == DerivativeFormat.WEBP);
    }

    private static boolean isJpeg(byte[] source) {
        return source.length >= 3 && (source[0] & 255) == 255 && (source[1] & 255) == 216
            && (source[2] & 255) == 255;
    }

    private static boolean isWebp(byte[] source) {
        return matches(source, 0, "RIFF") && matches(source, 8, "WEBP");
    }

    private static boolean isHeif(byte[] source) {
        if (source.length < 16 || !matches(source, 4, "ftyp")) return false;
        long boxSize = Integer.toUnsignedLong(java.nio.ByteBuffer.wrap(source).getInt());
        if (boxSize < 16 || boxSize > source.length || (boxSize - 16) % 4 != 0) return false;
        boolean heif = false;
        for (int offset = 8; offset < boxSize; offset += 4) {
            if (offset == 12) continue; // minor version is not a brand
            if (matches(source, offset, "avif") || matches(source, offset, "avis")) return false;
            heif |= matches(source, offset, "heic") || matches(source, offset, "heix")
                || matches(source, offset, "hevc") || matches(source, offset, "hevx")
                || matches(source, offset, "mif1") || matches(source, offset, "msf1");
        }
        return heif;
    }

    private static boolean matches(byte[] source, int offset, String signature) {
        if (source.length - offset < signature.length()) return false;
        for (int i = 0; i < signature.length(); i++) {
            if (source[offset + i] != signature.charAt(i)) return false;
        }
        return true;
    }

    public DerivedImages derive(byte[] source, DerivativeSpec thumbnail, DerivativeSpec preview) {
        validate(thumbnail);
        if (preview != null) {
            validate(preview);
        }
        NativeResult result = diagnostics.measure(timings -> Native.derive(
            source, thumbnail.maxWidth(), thumbnail.quality() * 100,
            preview == null ? 0 : preview.maxWidth(), preview == null ? 0 : preview.quality() * 100,
            timings));
        return new DerivedImages(result.width(), result.height(),
            new DerivedImage(result.thumbnail(), DerivativeFormat.WEBP),
            result.preview() == null ? null : new DerivedImage(result.preview(), DerivativeFormat.WEBP));
    }

    private static void validate(DerivativeSpec spec) {
        if (spec.maxWidth() <= 0 || spec.maxWidth() > 16383 || !Float.isFinite(spec.quality())
            || spec.quality() <= 0 || spec.quality() > 1 || spec.format() != DerivativeFormat.WEBP) {
            throw new IllegalArgumentException("libvips 파생본 크기·품질·포맷이 유효하지 않습니다");
        }
    }

    // 원본 표시 치수와 두 WebP만 JVM에 전달한다. 원본 픽셀은 native 호출 안에서 해제한다.
    record NativeResult(int width, int height, byte[] thumbnail, byte[] preview) { }

    private static final class Native {
        static {
            System.loadLibrary("sssok_vips");
            init();
        }

        static void initialize() { }
        private static native void init();
        private static native NativeResult derive(byte[] source, int thumbnailWidth, float thumbnailQuality,
                                                   int previewWidth, float previewQuality, long[] timings);
    }
}
