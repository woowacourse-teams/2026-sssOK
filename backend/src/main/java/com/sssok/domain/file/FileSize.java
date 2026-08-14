package com.sssok.domain.file;

import com.sssok.domain.file.exception.InvalidFileSizeException;

public record FileSize(long bytes) {

    private static final long COMPRESSION_THRESHOLD_BYTES = 500L * 1024;

    public FileSize {
        if (bytes <= 0) {
            throw new InvalidFileSizeException("파일 크기는 0보다 커야 합니다: " + bytes);
        }
    }

    public static FileSize ofKilobytes(long kilobytes) {
        return new FileSize(kilobytes * 1024);
    }

    public static FileSize ofMegabytes(long megabytes) {
        return new FileSize(megabytes * 1024 * 1024);
    }

    public boolean exceeds(long limitBytes) {
        return bytes > limitBytes;
    }

    public boolean isBelowCompressionThreshold() {
        return bytes < COMPRESSION_THRESHOLD_BYTES;
    }
}
