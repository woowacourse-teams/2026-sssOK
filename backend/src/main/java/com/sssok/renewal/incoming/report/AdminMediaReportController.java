package com.sssok.renewal.incoming.report;

import com.sssok.presentation.api.common.ApiResponse;
import com.sssok.presentation.auth.AuthAdmin;
import com.sssok.renewal.application.report.DeleteReportedMediaService;
import com.sssok.renewal.application.report.MediaReportAssigneeService;
import com.sssok.renewal.application.report.MediaReportQueryService;
import com.sssok.renewal.application.report.MediaReportStatusService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "관리자 사진 신고", description = "관리자가 접수된 사진 신고를 확인하고 처리한다. 일반 관리자와 슈퍼관리자 모두 할 수 있다.")
@RestController
@RequestMapping("/api/v1/admin/media-reports")
@RequiredArgsConstructor
public class AdminMediaReportController {

    private final MediaReportQueryService mediaReportQueryService;
    private final MediaReportAssigneeService mediaReportAssigneeService;
    private final MediaReportStatusService mediaReportStatusService;
    private final DeleteReportedMediaService deleteReportedMediaService;

    @Operation(
        summary = "신고 목록 조회",
        description = """
            접수된 사진 신고를 접수 최신순으로 조회한다. cursor 를 생략하면 가장 최신부터 내려주고, \
            다음 페이지는 직전 응답의 nextCursor 를 그대로 넣어 요청한다. size 는 기본 20, 최대 100 이며 \
            범위를 벗어나거나 없는 신고 ID 를 cursor 로 보내면 400이 난다.

            목록에는 신고 내용(방 ID·방 이름·대상 미디어 ID·신고자 ID·닉네임·사유·접수 시각·담당자·\
            처리 상태)만 싣는다. 방 이름·닉네임은 신고 당시 값이다. 대상 사진은 단건 조회에서 본다.

            status 는 처리 상태다. 새 신고는 UNREFLECTED(미처리)이고, 대상 사진을 삭제하면 \
            REFLECTED(처리완료)가 된다. 상태 변경 API 로 직접 바꿀 수도 있다. \
            assignee 는 담당자이며 정하지 않았으면 null 이다.

            관리자 토큰이 아니면 401, 권한이 없는 관리자면 403이 난다.\
            """
    )
    @GetMapping
    public ApiResponse<MediaReportsResponse> findLatest(
        @Parameter(hidden = true) @AuthAdmin Long adminId,
        @Parameter(description = "직전 응답의 nextCursor. 없으면 최신부터")
        @RequestParam(required = false) Long cursor,
        @Parameter(description = "한 번에 가져올 개수. 기본 20, 최대 100")
        @RequestParam(required = false) Integer size
    ) {
        return ApiResponse.of(MediaReportsResponse.from(mediaReportQueryService.findLatest(adminId, cursor, size)));
    }

    @Operation(
        summary = "신고 단건 조회",
        description = """
            신고 한 건과 대상 사진을 조회한다. 어떤 사진인지 볼 수 있게 thumbnailUrl·displayUrl 에 서명 URL 을 \
            싣는다. 사진이 이미 갤러리에서 지워졌으면 mediaDeleted 가 true 이고 URL 은 null 이다 — 신고 기록은 \
            사진과 별개로 남는다. reportCountOfMedia 는 같은 사진에 들어온 신고 수다.

            없는 신고 ID 면 404가 난다.\
            """
    )
    @GetMapping("/{reportId}")
    public ApiResponse<MediaReportResponse> findById(
        @Parameter(hidden = true) @AuthAdmin Long adminId,
        @Parameter(description = "조회할 신고의 ID") @PathVariable Long reportId
    ) {
        return ApiResponse.of(MediaReportResponse.from(mediaReportQueryService.findById(adminId, reportId)));
    }

    @Operation(
        summary = "신고 담당자 변경",
        description = """
            신고의 담당자를 정한다. assignee 에 MAICHAN(마이찬)·HAENI(해니)·YUNDOL(윤돌)·HYUNMIBAP(현미밥) \
            중 하나를 보낸다. null 로 보내거나 빼면 담당자를 비운다.

            그 외의 값을 보내면 400, 없는 신고 ID 면 404가 난다. 응답은 단건 조회 결과와 같다.\
            """
    )
    @PatchMapping("/{reportId}/assignee")
    public ApiResponse<MediaReportResponse> changeAssignee(
        @Parameter(hidden = true) @AuthAdmin Long adminId,
        @Parameter(description = "담당자를 바꿀 신고의 ID") @PathVariable Long reportId,
        @RequestBody UpdateMediaReportAssigneeRequest request
    ) {
        return ApiResponse.of(MediaReportResponse.from(
            mediaReportAssigneeService.changeAssignee(adminId, reportId, request.assignee())));
    }

    @Operation(
        summary = "신고 처리 상태 변경",
        description = """
            신고의 처리 상태를 바꾼다. status 에 UNREFLECTED(미처리)·REFLECTED(처리완료) 중 하나를 보낸다. \
            사진을 지우지 않고 처리완료로 넘기거나, 처리완료를 미처리로 되돌릴 때 쓴다. 이 신고 한 건만 바뀐다.

            그 외의 값이나 빈 값을 보내면 400, 없는 신고 ID 면 404가 난다. 응답은 단건 조회 결과와 같다.\
            """
    )
    @PatchMapping("/{reportId}/status")
    public ApiResponse<MediaReportResponse> changeStatus(
        @Parameter(hidden = true) @AuthAdmin Long adminId,
        @Parameter(description = "상태를 바꿀 신고의 ID") @PathVariable Long reportId,
        @RequestBody UpdateMediaReportStatusRequest request
    ) {
        return ApiResponse.of(MediaReportResponse.from(
            mediaReportStatusService.changeStatus(adminId, reportId, request.status())));
    }

    @Operation(
        summary = "신고된 사진 삭제",
        description = """
            신고 대상 사진을 갤러리에서 삭제한다. 사용자가 사진을 지울 때와 같이 방 안 사용자에게 \
            삭제가 실시간으로 반영되고, 스토리지 원본·파생본도 정리된다.

            삭제하면 같은 사진에 들어온 신고가 모두 REFLECTED(처리완료)로 바뀐다. 사진이 이미 지워졌어도 \
            실패하지 않고 상태만 처리완료로 맞춘다. 없는 신고 ID 면 404가 난다. 응답은 단건 조회 결과와 같다.\
            """
    )
    @DeleteMapping("/{reportId}/media")
    public ApiResponse<MediaReportResponse> deleteMedia(
        @Parameter(hidden = true) @AuthAdmin Long adminId,
        @Parameter(description = "대상 사진을 지울 신고의 ID") @PathVariable Long reportId
    ) {
        return ApiResponse.of(MediaReportResponse.from(deleteReportedMediaService.deleteMedia(adminId, reportId)));
    }
}
