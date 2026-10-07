package com.sssok.presentation.api.media;

import com.sssok.application.search.SearchImagesService;
import com.sssok.presentation.api.common.ApiResponse;
import com.sssok.presentation.api.media.docs.ImageSearchApi;
import java.util.List;
import com.sssok.presentation.auth.AuthMember;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/rooms/{roomId}/media/search")
@RequiredArgsConstructor
public class ImageSearchController implements ImageSearchApi {
    private final SearchImagesService searchImagesService;

    @Override
    @GetMapping(produces = "application/json")
    public ApiResponse<List<ImageSearchMatchResponse>> search(
        @PathVariable Long roomId,
        @RequestParam(required = false) String query,
        @AuthMember Long memberId
    ) {
        List<ImageSearchMatchResponse> matches = searchImagesService.search(roomId, query, memberId).stream()
            .map(ImageSearchMatchResponse::from)
            .toList();
        return ApiResponse.of(matches);
    }
}
