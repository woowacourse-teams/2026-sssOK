package com.sssok.application.media;

import java.util.List;

public record MediaPage(
    List<MediaDetail> items,
    MediaCursor nextCursor,
    boolean hasNext,
    long totalCount
) {
}
