package com.sssok.application.media;

import java.util.List;

public record MediaSelection(MediaSelectionMode mode, List<Long> ids) {

    public static MediaSelection include(List<Long> ids) {
        return new MediaSelection(MediaSelectionMode.INCLUDE, ids);
    }

    public static MediaSelection exclude(List<Long> ids) {
        return new MediaSelection(MediaSelectionMode.EXCLUDE, ids);
    }
}
