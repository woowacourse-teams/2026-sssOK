package com.sssok.presentation.api.media;

import com.sssok.application.media.MediaDetail;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

// 배열을 그대로 내리지 않고 한 겹 감싼다. 나중에 페이지네이션이 붙어도
// 필드만 늘리면 되고, 이미 붙인 클라이언트가 깨지지 않는다.
public record MediaListResponse(
    @Schema(description = "최신순으로 정렬된 미디어 목록") List<MediaResponse> items
) {
    public static MediaListResponse from(List<MediaDetail> details, String accessToken) {
        return new MediaListResponse(details.stream()
            .map(detail -> MediaResponse.from(detail, accessToken))
            .toList());
    }
}
