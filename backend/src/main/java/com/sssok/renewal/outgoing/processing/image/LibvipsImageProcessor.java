package com.sssok.renewal.outgoing.processing.image;

import com.sssok.domain.file.DerivativeFormat;
import com.sssok.renewal.outgoing.processing.image.ThumbnailatorImageProcessor.DerivativeSpec;
import com.sssok.renewal.outgoing.processing.image.ThumbnailatorImageProcessor.DerivedImage;
import com.sssok.renewal.outgoing.processing.image.ThumbnailatorImageProcessor.DerivedImages;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import com.sssok.renewal.outgoing.monitoring.UploadTracing;

/** 검증한 JPEG → WebP 경로만 대체한다. 다른 포맷과 EXIF 촬영 정보는 기존 구현이 담당한다. */
@Component
@ConditionalOnProperty(name = "media.image.processor", havingValue = "libvips")
public class LibvipsImageProcessor implements ImageDerivativeEngine {
    private final UploadTracing tracing;
    private final boolean stageDiagnostics;

    public LibvipsImageProcessor() {
        this(null, false);
    }

    @Autowired
    public LibvipsImageProcessor(UploadTracing tracing,
            @Value("${media.image.native-stage-diagnostics:false}") boolean stageDiagnostics) {
        this.tracing = tracing;
        this.stageDiagnostics = stageDiagnostics;
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
        // 진단 모드는 중간 픽셀을 확정하므로 원래 lazy 경로와 성능을 분리해 비교한다.
        if (tracing != null) tracing.nativeExecution(stageDiagnostics);
        long[] timings = stageDiagnostics ? new long[21] : null;
        NativeResult result;
        try {
            result = Native.derive(source, thumbnail.maxWidth(), thumbnail.quality() * 100,
                preview == null ? 0 : preview.maxWidth(), preview == null ? 0 : preview.quality() * 100,
                timings);
        } finally {
            if (timings != null && tracing != null) {
                var stages = new UploadTracing.Stage[] {UploadTracing.Stage.ORIENTATION,
                    UploadTracing.Stage.DECODE, UploadTracing.Stage.RESIZE, UploadTracing.Stage.RESIZE,
                    UploadTracing.Stage.ENCODE, UploadTracing.Stage.RESIZE, UploadTracing.Stage.ENCODE};
                String[] variants = {"source", "source", "shared", "thumbnail", "thumbnail", "preview", "preview"};
                for (int i = 0; i < stages.length; i++) {
                    if (timings[i * 3] != 0) {
                        tracing.nativeStage(stages[i], variants[i], timings[i * 3],
                            timings[i * 3 + 1], timings[i * 3 + 2] == 1);
                    }
                }
            }
        }
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
