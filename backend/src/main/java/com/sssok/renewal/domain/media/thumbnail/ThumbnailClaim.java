package com.sssok.renewal.domain.media.thumbnail;

import java.time.Instant;
import java.util.UUID;

public record ThumbnailClaim(Long mediaId, ThumbnailKind kind, UUID token, int attempt, Instant startedAt) {}
