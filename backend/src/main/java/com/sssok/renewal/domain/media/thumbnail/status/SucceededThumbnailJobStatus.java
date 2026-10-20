package com.sssok.renewal.domain.media.thumbnail.status;

public final class SucceededThumbnailJobStatus implements ThumbnailJobStatus {
    public static final SucceededThumbnailJobStatus INSTANCE = new SucceededThumbnailJobStatus();

    private SucceededThumbnailJobStatus() {
    }

    @Override
    public String name() {
        return "SUCCEEDED";
    }

}
