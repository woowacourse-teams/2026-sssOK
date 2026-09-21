package com.sssok.application.media;

import java.time.Instant;

public record MediaCursor(
    Long roomId,
    Long folderId,
    Long requesterId,
    MediaUploaderFilter uploader,
    Instant lastCreatedAt,
    Long lastMediaId
) {

    public MediaCursor(Long roomId, Long folderId, Instant lastCreatedAt, Long lastMediaId) {
        this(roomId, folderId, null, MediaUploaderFilter.ALL, lastCreatedAt, lastMediaId);
    }
}
