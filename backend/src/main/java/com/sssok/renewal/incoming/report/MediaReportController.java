package com.sssok.renewal.incoming.report;

import com.sssok.presentation.api.common.ApiResponse;
import com.sssok.renewal.application.report.ReportMediaService;
import com.sssok.renewal.incoming.auth.AuthMember;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "미디어 신고", description = "방 안 사진을 신고한다")
@RestController
@RequestMapping("/api/v1/rooms/{roomId}/media/{mediaId}/reports")
@RequiredArgsConstructor
public class MediaReportController {
    private final ReportMediaService reportMediaService;

    @Operation(
        summary = "사진 신고",
        description = "사진 단건 조회 화면에서 사진 한 장을 신고 사유 하나로 신고한다. 서버가 대상 사진과 사유, "
            + "접수 시각을 저장한다. reason 은 INAPPROPRIATE_CONTENT(선정적·폭력적 콘텐츠), "
            + "PRIVACY_VIOLATION(개인정보·초상권 침해), COPYRIGHT_VIOLATION(저작권 침해), "
            + "HARASSMENT(괴롭힘·혐오 표현) 중 하나이며, 없거나 다른 값이면 400. "
            + "같은 사람이 같은 사진을 다시 신고하면 409. "
            + "없는 미디어·다른 방 미디어·아직 실물이 없는 미디어(RESERVED/FAILED)는 404, "
            + "입장하지 않은 사용자는 403, 만료·삭제된 방은 410이 난다."
    )
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ReportMediaResponse> report(
        @Parameter(hidden = true) @AuthMember Long memberId,
        @Parameter(description = "방 조회 응답의 roomId") @PathVariable Long roomId,
        @Parameter(description = "신고할 미디어 ID") @PathVariable Long mediaId,
        @Valid @RequestBody ReportMediaRequest request
    ) {
        return ApiResponse.of(ReportMediaResponse.from(
            reportMediaService.report(request.toCommand(roomId, mediaId, memberId))));
    }
}
