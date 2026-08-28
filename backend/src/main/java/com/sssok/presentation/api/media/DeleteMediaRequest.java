package com.sssok.presentation.api.media;

import java.util.List;

public record DeleteMediaRequest(List<Long> mediaIds) {
}
