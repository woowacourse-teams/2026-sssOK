package com.sssok.renewal.outgoing.processing.image;

import com.sssok.domain.file.DerivativeFormat;
import com.sssok.renewal.outgoing.processing.image.ThumbnailatorImageProcessor.DerivativeSpec;
import com.sssok.renewal.outgoing.processing.image.ThumbnailatorImageProcessor.DerivedImage;
import com.sssok.renewal.outgoing.processing.image.ThumbnailatorImageProcessor.DerivedImages;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 검증한 JPEG → WebP 경로만 대체한다. 다른 포맷과 EXIF 촬영 정보는 기존 구현이 담당한다. */
@Component
@ConditionalOnProperty(name = "media.image.processor", havingValue = "libvips")
public class LibvipsImageProcessor implements ImageDerivativeEngine {
    public LibvipsImageProcessor() {
        Native.initialize();
    }

    public boolean supports(byte[] source, DerivativeSpec thumbnail, DerivativeSpec preview) {
        return source.length >= 3 && (source[0] & 255) == 255 && (source[1] & 255) == 216
            && (source[2] & 255) == 255 && thumbnail.format() == DerivativeFormat.WEBP
            && (preview == null || preview.format() == DerivativeFormat.WEBP);
    }

    public DerivedImages derive(byte[] source, DerivativeSpec thumbnail, DerivativeSpec preview) {
        validate(thumbnail);
        if (preview != null) {
            validate(preview);
        }
        NativeResult result = Native.derive(source, thumbnail.maxWidth(), thumbnail.quality() * 100,
            preview == null ? 0 : preview.maxWidth(), preview == null ? 0 : preview.quality() * 100);
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
                                                   int previewWidth, float previewQuality);
    }
}
