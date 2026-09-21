package com.sssok.presentation.api.media;

import com.sssok.application.media.MediaDetail;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "페이지네이션 없이 반환하는 미디어 전체 목록")
public record AllMediaListResponse(
    @Schema(description = "createdAt·mediaId 기준 최신순으로 정렬된 전체 미디어 목록")
    List<MediaResponse> items
) {

    public static AllMediaListResponse from(List<MediaDetail> details) {
        return new AllMediaListResponse(details.stream()
            .map(MediaResponse::from)
            .toList());
    }
}
