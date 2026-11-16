package com.sssok.renewal.outgoing.processing.image;

import com.sssok.domain.file.DerivativeFormat;
import com.sssok.domain.file.GeoPoint;
import com.sssok.renewal.outgoing.processing.image.ImageDerivativeOperations.SourceSize;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ThumbnailatorImageProcessor {
    private final ImageDerivativeOperations operations;

    public Optional<DerivedImages> derive(byte[] source, DerivativeSpec thumbnail,
                                          DerivativeSpec preview) {
        ExifOrientation orientation = operations.readOrientation(source);
        int requiredWidth = preview == null ? thumbnail.maxWidth()
            : Math.max(thumbnail.maxWidth(), preview.maxWidth());
        var decoded = operations.decode(source, orientation, requiredWidth);
        if (decoded == null) {
            return Optional.empty();
        }
        SourceSize sourceSize = new SourceSize(decoded.sourceWidth(), decoded.sourceHeight());
        BufferedImage original = decoded.pixels();
        try {
            if (preview != null && preview.maxWidth() >= thumbnail.maxWidth()) {
                // 압축된 프리뷰를 다시 읽지 않고, 인코딩 전 픽셀을 공유한다.
                // 방향은 프리뷰 생성 시 한 번만 적용한다.
                BufferedImage previewImage = operations.resizeAndOrient(original, orientation, preview.maxWidth(), sourceSize);
                BufferedImage thumbnailImage = operations.resizeAndOrient(
                    previewImage, ExifOrientation.NORMAL, thumbnail.maxWidth(), sourceSize);
                return Optional.of(new DerivedImages(
                    sourceSize.width(),
                    sourceSize.height(),
                    operations.encodeDerivative(thumbnailImage, thumbnail),
                    operations.encodeDerivative(previewImage, preview)));
            }
            // 프리뷰가 없거나 더 작게 설정되면 썸네일의 해상도를 낮추지 않는다.
            return Optional.of(new DerivedImages(
                sourceSize.width(),
                sourceSize.height(),
                operations.encodeDerivative(operations.resizeAndOrient(original, orientation, thumbnail.maxWidth(), sourceSize), thumbnail),
                preview == null ? null : operations.encodeDerivative(
                    operations.resizeAndOrient(original, orientation, preview.maxWidth(), sourceSize), preview)));
        } catch (IOException e) {
            // 디코딩을 넘긴 뒤의 축소·인코딩 실패는 원본이 깨졌다는 근거가 되지 못한다. 네이티브
            // WebP 라이터가 한 번 흔들린 것을 빈 값으로 돌려주면 멀쩡한 사진이 영구 FAILED 가
            // 되므로, 밖으로 내보내 PROCESSING 으로 남기고 회수 배치가 다시 태우게 한다.
            throw new UncheckedIOException(e);
        }
    }

    public CaptureInfo readCaptureInfo(byte[] source) {
        return operations.readCaptureInfo(source);
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
