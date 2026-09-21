package com.sssok.application.media;

public enum MediaUploaderFilter {
    ALL,
    ME,
    OTHERS;

    public boolean includes(Long uploaderId, Long requesterId) {
        return switch (this) {
            case ALL -> true;
            case ME -> uploaderId.equals(requesterId);
            case OTHERS -> !uploaderId.equals(requesterId);
        };
    }

    public static MediaUploaderFilter defaultIfNull(MediaUploaderFilter uploader) {
        return uploader == null ? ALL : uploader;
    }
}
