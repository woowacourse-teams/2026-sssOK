package com.sssok.renewal.outgoing.processing.image;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

// 원본 치수는 헤더에서 보존하고, JPEG 픽셀은 파생본에 필요한 크기 이상으로만 읽는다.
class DerivativeImageDecoder {
    DecodedImage decode(byte[] source, ExifOrientation orientation, int requiredWidth) {
        if (requiredWidth <= 0) {
            throw new IllegalArgumentException("파생본 너비는 양수여야 합니다");
        }
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(source))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                return null;
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                int displayWidth = orientation.displayWidthOf(width, height);
                var params = reader.getDefaultReadParam();
                // 다른 포맷은 기존 해상도로 읽는다. 플러그인별 축소 디코딩 효과는 별도 검증한다.
                if (reader.getFormatName().equalsIgnoreCase("JPEG")) {
                    int sampling = Math.max(1, displayWidth / requiredWidth);
                    params.setSourceSubsampling(sampling, sampling, 0, 0);
                }
                BufferedImage pixels = reader.read(0, params);
                return pixels == null ? null : new DecodedImage(pixels, displayWidth,
                    orientation.displayHeightOf(width, height));
            } finally {
                reader.dispose();
            }
        } catch (IOException | IllegalArgumentException e) {
            return null;
        }
    }

    record DecodedImage(BufferedImage pixels, int sourceWidth, int sourceHeight) {
    }
}
