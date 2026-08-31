package com.sssok.application.media;

import java.time.Instant;

// 조회 응답에 직접 실을 썸네일·원본 서명 URL과 각각의 만료 시각.
// 워커가 아직 만들지 않았거나(썸네일) 아직 READY가 아니면(원본) 둘 다 null이다.
public record MediaUrls(
    String thumbnailUrl,
    Instant thumbnailUrlExpiresAt,
    String originalUrl,
    Instant originalUrlExpiresAt
) {

    public static final MediaUrls NONE = new MediaUrls(null, null, null, null);
}
