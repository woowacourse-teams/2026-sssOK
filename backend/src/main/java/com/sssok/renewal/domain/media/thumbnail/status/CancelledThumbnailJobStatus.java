package com.sssok.renewal.domain.media.thumbnail.status;

public final class CancelledThumbnailJobStatus implements ThumbnailJobStatus {
    public static final CancelledThumbnailJobStatus INSTANCE = new CancelledThumbnailJobStatus();

    private CancelledThumbnailJobStatus() {
    }

    @Override
    public String name() {
        return "CANCELLED";
    }

}
