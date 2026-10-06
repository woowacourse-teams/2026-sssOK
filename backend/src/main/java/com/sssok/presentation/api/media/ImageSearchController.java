package com.sssok.presentation.api.media;

import com.sssok.application.search.SearchImagesService;
import com.sssok.presentation.api.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/rooms/{roomId}/media/search")
@RequiredArgsConstructor
public class ImageSearchController {
    private final SearchImagesService service;

    @Operation(summary = "자연어 이미지 검색", description = "검색어는 공백 정리 후 1~200자. "
        + "해당 방의 분석 완료 이미지 중 서버 임계값 이상을 유사도 내림차순으로 모두 반환한다. "
        + "동점은 mediaId 오름차순. 결과 없음은 빈 배열, 잘못된 검색어는 400, "
        + "기능 비활성화·임계값 미설정·임베딩 장애는 503이다. 유사도는 정답 확률이 아니다.")
    @GetMapping
    public ApiResponse<List<MatchResponse>> search(@PathVariable Long roomId, @RequestParam(required = false) String query) {
        return ApiResponse.of(service.search(roomId, query).stream()
            .map(result -> new MatchResponse(MediaResponse.from(result.media()), result.similarity())).toList());
    }

    public record MatchResponse(MediaResponse media, double similarity) {}
}
