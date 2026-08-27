package com.sssok.domain.file;

import com.sssok.domain.file.exception.UnsupportedMediaTypeException;

import java.util.Arrays;
import java.util.Locale;

public enum MediaType {

    JPEG("jpg", "image/jpeg", Kind.IMAGE),
    PNG("png", "image/png", Kind.IMAGE),
    GIF("gif", "image/gif", Kind.IMAGE),
    MP4("mp4", "video/mp4", Kind.VIDEO),
    WEBM("webm", "video/webm", Kind.VIDEO),
    MOV("mov", "video/quicktime", Kind.VIDEO);

    private static final long IMAGE_MAX_BYTES = 10L * 1024 * 1024;
    private static final long VIDEO_MAX_BYTES = 1024L * 1024 * 1024;

    private final String extension;
    private final String contentType;
    private final Kind kind;

    MediaType(String extension, String contentType, Kind kind) {
        this.extension = extension;
        this.contentType = contentType;
        this.kind = kind;
    }

    public static MediaType fromExtension(String extension) {
        String normalized = normalize(extension);
        return Arrays.stream(values())
                .filter(type -> type.matchesExtension(normalized))
                .findFirst()
                .orElseThrow(() -> new UnsupportedMediaTypeException(extension));
    }

    // 클라이언트가 보낸 MIME 은 신뢰하지 않고 허용 목록에 있는지 대조한다.
    // 여기서 통과한 값 그대로 서명해야 업로드가 깨지지 않는다(R2_PRESIGNED_UPLOAD.md 참고).
    public static MediaType fromMimeType(String mimeType) {
        String normalized = normalize(mimeType);
        return Arrays.stream(values())
                .filter(type -> type.contentType.equals(normalized))
                .findFirst()
                .orElseThrow(() -> new UnsupportedMediaTypeException(mimeType));
    }

    public static MediaType fromFileName(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            throw new UnsupportedMediaTypeException(fileName);
        }
        return fromExtension(fileName.substring(fileName.lastIndexOf('.') + 1));
    }

    private static String normalize(String extension) {
        if (extension == null) {
            throw new UnsupportedMediaTypeException(null);
        }
        return extension.trim().toLowerCase(Locale.ROOT);
    }

    private boolean matchesExtension(String normalized) {
        if (this == JPEG) {
            return normalized.equals("jpg") || normalized.equals("jpeg");
        }
        return extension.equals(normalized);
    }

    public boolean isImage() {
        return kind == Kind.IMAGE;
    }

    public boolean isVideo() {
        return kind == Kind.VIDEO;
    }

    public boolean preservesAnimation() {
        return this == GIF;
    }

    public long maxBytes() {
        return isImage() ? IMAGE_MAX_BYTES : VIDEO_MAX_BYTES;
    }

    public String extension() {
        return extension;
    }

    public String contentType() {
        return contentType;
    }

    private enum Kind {
        IMAGE, VIDEO
    }
}
