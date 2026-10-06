package com.sssok.presentation.api.media.docs;

import com.sssok.presentation.api.common.ErrorResponse;
import com.sssok.presentation.api.media.ImageSearchMatchResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;

@Tag(name = "미디어 조회")
public interface ImageSearchApi {
    @Operation(
        summary = "자연어 이미지 검색",
        description = """
            참여 중인 방의 이미지를 자연어 검색어로 조회한다. Authorization에 Bearer 토큰을 전달한다.

            검색어는 연속 공백과 앞뒤 공백을 정리한 후 1~200자여야 한다.
            예: query=바닷가에서 찍은 사진

            분석이 완료된 이미지 중 서버 임계값 이상의 결과를 모두 반환한다.
            결과 개수 제한은 없으며, 유사도 내림차순으로 정렬한다. 동점은 mediaId 오름차순이다.
            동영상과 분석 대기·실패 이미지는 검색 대상에 포함하지 않는다.

            응답 data는 media와 similarity를 가진 배열이다. 결과가 없으면 data는 빈 배열이다.
            similarity는 코사인 유사도이며 정답 확률이 아니다.
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200",
            description = "검색 성공. 결과가 없으면 data: []"),
        @ApiResponse(responseCode = "400",
            description = "INVALID_SEARCH_QUERY: 검색어 누락·공백 또는 정리 후 200자 초과",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401",
            description = "인증 토큰 누락·만료·유효하지 않음",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403",
            description = "방 참여 권한 없음",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404",
            description = "방을 찾을 수 없음",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "410",
            description = "ROOM_EXPIRED: 만료되거나 삭제된 방",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "503",
            description = "IMAGE_SEARCH_UNAVAILABLE: 기능 비활성화·임계값 미설정 또는 AI 호출 실패",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    com.sssok.presentation.api.common.ApiResponse<List<ImageSearchMatchResponse>> search(
        @Parameter(description = "방 조회 응답의 roomId", example = "6") Long roomId,
        @Parameter(description = "공백 정리 후 1~200자의 검색어", required = true,
            example = "바닷가에서 찍은 사진") String query
    );
}
