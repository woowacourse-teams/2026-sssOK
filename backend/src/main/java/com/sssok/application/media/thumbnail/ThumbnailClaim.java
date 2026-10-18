package com.sssok.application.media.thumbnail;

import com.sssok.common.monitoring.MediaProcessingMetrics.Kind;
import java.time.Instant;
import java.util.UUID;

public record ThumbnailClaim(Long mediaId, Kind kind, UUID token, int attempt, Instant startedAt) {}
