package com.sssok.presentation.api.media;

import com.sssok.application.media.MediaPage;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

// 배열을 그대로 내리지 않고 한 겹 감싼다. 나중에 페이지네이션이 붙어도
// 필드만 늘리면 되고, 이미 붙인 클라이언트가 깨지지 않는다.
public record MediaListResponse(
    @Schema(description = "최신순으로 정렬된 미디어 목록") List<MediaResponse> items,
    @Schema(description = "다음 페이지 커서. 마지막 페이지이면 null") String nextCursor,
    boolean hasNext,
    @Schema(description = "요청 시점의 조회 조건에 해당하는 전체 미디어 개수") long totalCount
) {
    public static MediaListResponse from(MediaPage page, String nextCursor) {
        return new MediaListResponse(
            page.items().stream().map(MediaResponse::from).toList(),
            nextCursor,
            page.hasNext(),
            page.totalCount());
    }
}
