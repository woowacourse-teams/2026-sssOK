package com.sssok.application.media;

import java.util.List;

public record MediaDeletedEvent(Long roomId, List<Long> mediaIds) {
}
