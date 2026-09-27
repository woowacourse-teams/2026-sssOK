package com.sssok.application.media;

import java.time.Instant;

// 조회 응답에 직접 실을 서명 URL 과 각각의 만료 시각.
//
// 어떤 것이 채워지는지는 화면마다 다르다 (MediaUrlResolver 참고):
//  - 목록: thumbnail 만. 프리뷰 URL 은 상세를 열었을 때만 내주어, 실제로 열어보는 사진에
//    대해서만 1600px 요청이 일어나게 한다.
//  - 상세: 사진이면 preview, 영상이면 original.
public record MediaUrls(
    String thumbnailUrl,
    Instant thumbnailUrlExpiresAt,
    String previewUrl,
    Instant previewUrlExpiresAt,
    String originalUrl,
    Instant originalUrlExpiresAt
) {

    public static final MediaUrls NONE = new MediaUrls(null, null, null, null, null, null);
}
