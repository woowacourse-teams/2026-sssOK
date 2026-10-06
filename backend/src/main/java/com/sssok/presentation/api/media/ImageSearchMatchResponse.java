package com.sssok.presentation.api.media;

import com.sssok.application.search.ImageSearchResult;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "검색어와 유사한 이미지와 유사도 점수")
public record ImageSearchMatchResponse(
    @Schema(description = "기존 미디어 조회와 동일한 형식의 이미지 정보") MediaResponse media,
    @Schema(description = "코사인 유사도. 서버 임계값 이상이며 정답 확률을 의미하지 않는다",
        example = "0.72", minimum = "-1", maximum = "1") double similarity
) {
    public static ImageSearchMatchResponse from(ImageSearchResult result) {
        return new ImageSearchMatchResponse(MediaResponse.from(result.media()), result.similarity());
    }
}
