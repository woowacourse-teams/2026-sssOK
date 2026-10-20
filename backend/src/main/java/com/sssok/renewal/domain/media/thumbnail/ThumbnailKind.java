package com.sssok.renewal.domain.media.thumbnail;

public enum ThumbnailKind {
    IMAGE, VIDEO;

    public static ThumbnailKind of(boolean video) {
        return video ? VIDEO : IMAGE;
    }
}
