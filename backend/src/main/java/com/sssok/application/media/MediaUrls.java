package com.sssok.application.media;

import java.time.Instant;

// 조회 응답에 직접 실을 서명 URL 과 각각의 만료 시각.
//
public record MediaUrls(
    String thumbnailUrl,
    Instant thumbnailUrlExpiresAt,
    String displayUrl,
    Instant displayUrlExpiresAt
) {

    public static final MediaUrls NONE = new MediaUrls(null, null, null, null);
}
