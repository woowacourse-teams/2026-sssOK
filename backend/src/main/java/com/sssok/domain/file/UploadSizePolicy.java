package com.sssok.domain.file;

// 업로드 크기 상한. 값 자체는 설정(upload.image-max-size / upload.video-max-size)에서 오고,
// 도메인은 받은 값으로 판정만 한다.
public record UploadSizePolicy(long imageMaxBytes, long videoMaxBytes) {

    public UploadSizePolicy {
        requirePositive(imageMaxBytes, "이미지");
        requirePositive(videoMaxBytes, "영상");
    }

    public long maxBytesFor(MediaType mediaType) {
        return mediaType.isImage() ? imageMaxBytes : videoMaxBytes;
    }

    private static void requirePositive(long bytes, String label) {
        if (bytes <= 0) {
            throw new IllegalArgumentException(label + " 업로드 상한은 0보다 커야 합니다: " + bytes);
        }
    }
}
