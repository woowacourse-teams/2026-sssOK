package com.sssok.application.media;

import java.time.Instant;

public record MediaCursor(
    Long roomId,
    Long folderId,
    Instant lastCreatedAt,
    Long lastMediaId
) {
}
