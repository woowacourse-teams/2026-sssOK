package com.sssok.renewal.incoming.organization;

import com.sssok.presentation.api.common.ApiResponse;
import com.sssok.renewal.application.organization.job.OrganizeApplyService;
import com.sssok.renewal.application.organization.job.OrganizeCancelService;
import com.sssok.renewal.application.organization.job.OrganizeJobQueryService;
import com.sssok.renewal.application.organization.job.OrganizeResultService;
import com.sssok.renewal.application.organization.job.OrganizeRetryPolicy;
import com.sssok.renewal.application.organization.job.OrganizeRetryService;
import com.sssok.renewal.application.organization.job.OrganizeStartService;
import com.sssok.renewal.domain.organization.OrganizeJob;
import com.sssok.renewal.incoming.auth.AuthMember;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// renewal 패키지는 /api/v1 자동 접두사 대상이 아니라 경로에 직접 적는다.
// 방 존재·만료·입장 여부는 RoomMembershipInterceptor 가 먼저 걸러준다.
@Tag(name = "사진 정리", description = "방의 사진을 인물별·비슷한 사진끼리 묶는 정리 작업. 시작(버튼·문장) → 진행·완료는 방 SSE "
    + "(organize.job.updated) → 검토 결과 조회 → 폴더 반영 또는 취소. 분석 실패는 재시도할 수 있다. 분석은 비동기이며 "
    + "작업은 요청자 본인만 조회·반영·취소·재시도할 수 있다. 시작·재시도·반영에는 Idempotency-Key 헤더가 필요하다.")
@RestController
@RequestMapping("/api/v1/rooms/{roomId}/organize-jobs")
@RequiredArgsConstructor
public class OrganizeJobController {

    private final OrganizeStartService startService;
    private final OrganizeJobQueryService queryService;
    private final OrganizeResultService resultService;
    private final OrganizeCancelService cancelService;
    private final OrganizeApplyService applyService;
    private final OrganizeRetryService retryService;
    private final OrganizeRetryPolicy retryPolicy;

    @Operation(summary = "버튼으로 정리 시작",
        description = "kind(PERSON·SIMILAR)를 직접 골라 시작한다. 모델을 호출하지 않는다. Idempotency-Key(영숫자·-·_ 16~64자) 필수. "
            + "방에 활성 작업(진행 중·검토 중)이 있으면 409, 익명 회원이 이 방의 체험을 이미 썼으면 403, OAuth 회원의 접수 간격 "
            + "(60초) 안이면 429(Retry-After), 분석할 사진이 없거나 한도를 넘으면 422, 기능이 꺼져 있거나 분석 서버를 쓸 수 없으면 503이다. "
            + "같은 키로 다시 보내면 처음 접수한 작업을 돌려준다.")
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<OrganizeJobResponse> start(
        @Parameter(hidden = true) @AuthMember Long memberId,
        @Parameter(description = "방 조회 응답의 roomId") @PathVariable Long roomId,
        @Parameter(description = "같은 요청의 재전송을 가려내는 고유 키") @RequestHeader(value = "Idempotency-Key", required = false)
        String idempotencyKey,
        @Valid @RequestBody StartOrganizeJobRequest request
    ) {
        OrganizeJob job = startService.startByKind(roomId, memberId, request.kind(), idempotencyKey);
        return ApiResponse.of(OrganizeJobResponse.from(job, retryPolicy));
    }

    @Operation(summary = "문장으로 정리 시작",
        description = "문장을 모델이 해석해 정리 종류를 고른다. Idempotency-Key 필수. 회원당 분당 5회 제한(429, Retry-After). 요청이 애매하면 "
            + "422 ORGANIZE_NEEDS_CLARIFICATION, 지원하지 않으면 422 ORGANIZE_UNSUPPORTED_REQUEST이며 이때는 작업을 만들지 않는다.")
    @PostMapping("/by-text")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<OrganizeJobResponse> startByText(
        @Parameter(hidden = true) @AuthMember Long memberId,
        @Parameter(description = "방 조회 응답의 roomId") @PathVariable Long roomId,
        @Parameter(description = "같은 요청의 재전송을 가려내는 고유 키") @RequestHeader(value = "Idempotency-Key", required = false)
        String idempotencyKey,
        @RequestBody StartOrganizeJobByTextRequest request
    ) {
        OrganizeJob job = startService.startByText(roomId, memberId, request.text(), idempotencyKey);
        return ApiResponse.of(OrganizeJobResponse.from(job, retryPolicy));
    }

    @Operation(summary = "화면 재진입 스냅샷",
        description = "항상 200. 방에 활성 작업이 있는지(roomBusy), 방 점유 버전(roomVersion), 요청자 본인의 작업(myJob)을 준다. "
            + "myJob 은 활성 작업을 우선하고 없으면 최근 3일 안에 끝난 작업 1개이며 없으면 null 이다. 다른 사람의 작업은 숨긴다. "
            + "anonymousTrialAvailable 은 익명 회원의 이 방 체험 가능 여부이며 가입 회원은 null 이다. "
            + "방 SSE 를 먼저 구독한 뒤 이 스냅샷을 받고, 이후 이벤트와 jobVersion·roomVersion 을 비교해 최신 값을 유지한다.")
    @GetMapping("/current")
    public ApiResponse<OrganizeCurrentResponse> current(
        @Parameter(hidden = true) @AuthMember Long memberId,
        @Parameter(description = "방 조회 응답의 roomId") @PathVariable Long roomId
    ) {
        return ApiResponse.of(OrganizeCurrentResponse.from(queryService.current(roomId, memberId), retryPolicy));
    }

    @Operation(summary = "작업 상태 단건 조회",
        description = "SSE 연결 복구나 특정 작업 확인용이다(정기 폴링 금지). 검토 기한이 지난 READY 는 EXPIRED 로 돌려준다. "
            + "남의 작업이거나 없는 작업이면 404.")
    @GetMapping("/{jobId}")
    public ApiResponse<OrganizeJobResponse> get(
        @Parameter(hidden = true) @AuthMember Long memberId,
        @Parameter(description = "방 조회 응답의 roomId") @PathVariable Long roomId,
        @Parameter(description = "작업 ID") @PathVariable UUID jobId
    ) {
        return ApiResponse.of(OrganizeJobResponse.from(queryService.get(roomId, memberId, jobId), retryPolicy));
    }

    @Operation(summary = "검토용 결과 조회",
        description = "검토 기한(분석 완료 +30분) 안의 READY, 보관 기한(분석 완료 +3일) 안의 APPLIED 에서만 가능하다. 그 밖의 진행 상태는 409, "
            + "검토가 만료됐으면 410 ORGANIZE_REVIEW_EXPIRED, 결과 보관이 끝났으면 410 ORGANIZE_RESULT_EXPIRED. 조회가 기한을 늘리지 않는다. "
            + "검토 중 삭제된 사진은 빼고 removedCount 로 알린다.")
    @GetMapping("/{jobId}/result")
    public ApiResponse<OrganizeResultResponse> result(
        @Parameter(hidden = true) @AuthMember Long memberId,
        @Parameter(description = "방 조회 응답의 roomId") @PathVariable Long roomId,
        @Parameter(description = "작업 ID") @PathVariable UUID jobId
    ) {
        return ApiResponse.of(OrganizeResultResponse.from(resultService.get(roomId, memberId, jobId)));
    }

    @Operation(summary = "선택한 그룹을 폴더로 반영",
        description = "검토 기한 안의 READY 작업에서 일반 그룹(groupId 가 있는 그룹)만 새 폴더로 만든다. 소그룹·미분류는 반영할 수 없다(400). "
            + "Idempotency-Key 필수. 같은 이름의 폴더가 있으면 409 ORGANIZE_FOLDER_NAME_CONFLICT, 선택한 사진이 사라졌으면 409 "
            + "ORGANIZE_TARGET_CHANGED, 이미 반영했으면 409 ORGANIZE_ALREADY_APPLIED, 검토가 만료됐으면 410이며 어느 경우에도 "
            + "일부만 반영하지 않는다. 덮어쓰기·병합은 하지 않는다.")
    @PostMapping("/{jobId}/apply")
    public ApiResponse<ApplyOrganizeJobResponse> apply(
        @Parameter(hidden = true) @AuthMember Long memberId,
        @Parameter(description = "방 조회 응답의 roomId") @PathVariable Long roomId,
        @Parameter(description = "작업 ID") @PathVariable UUID jobId,
        @Parameter(description = "같은 요청의 재전송을 가려내는 고유 키") @RequestHeader(value = "Idempotency-Key", required = false)
        String idempotencyKey,
        @RequestBody ApplyOrganizeJobRequest request
    ) {
        OrganizeApplyService.Applied applied =
            applyService.apply(roomId, memberId, jobId, request.toGroupRequests(), idempotencyKey);
        return ApiResponse.of(ApplyOrganizeJobResponse.from(applied));
    }

    @Operation(summary = "작업 취소",
        description = "QUEUED·RUNNING·READY 를 취소한다(READY 취소는 검토 포기). 취소하면 방의 활성 작업 제한이 풀린다. "
            + "이미 취소·실패·만료된 작업은 그대로 200, 반영 완료(APPLIED)는 409. 분석 서버에는 취소 기능이 없어 분석은 "
            + "계속될 수 있고 서버는 그 결과를 버린다.")
    @PostMapping("/{jobId}/cancel")
    public ApiResponse<OrganizeJobResponse> cancel(
        @Parameter(hidden = true) @AuthMember Long memberId,
        @Parameter(description = "방 조회 응답의 roomId") @PathVariable Long roomId,
        @Parameter(description = "작업 ID") @PathVariable UUID jobId
    ) {
        return ApiResponse.of(OrganizeJobResponse.from(cancelService.cancel(roomId, memberId, jobId), retryPolicy));
    }

    @Operation(summary = "실패한 작업 재시도",
        description = "retryable=true 인 FAILED 작업을 같은 jobId 로 다시 시도한다. Idempotency-Key 필수. 전달만 실패한 작업은 같은 "
            + "실행 ID 로 다시 보내고, 분석 서버가 실패로 끝낸 작업은 새 실행 회차를 만든다(작업당 최대 3회). 이미 진행 중이거나 검토 중이면 "
            + "200 과 최신 작업을 돌려준다. 재시도할 수 없으면 409 ORGANIZE_RETRY_NOT_ALLOWED, 대기 시간(retryAvailableAt) 전이면 "
            + "429(Retry-After), 그 사이 다른 작업이 방을 차지했으면 409 ORGANIZE_JOB_IN_PROGRESS 이다.")
    @PostMapping("/{jobId}/retry")
    public ResponseEntity<ApiResponse<OrganizeJobResponse>> retry(
        @Parameter(hidden = true) @AuthMember Long memberId,
        @Parameter(description = "방 조회 응답의 roomId") @PathVariable Long roomId,
        @Parameter(description = "작업 ID") @PathVariable UUID jobId,
        @Parameter(description = "같은 요청의 재전송을 가려내는 고유 키") @RequestHeader(value = "Idempotency-Key", required = false)
        String idempotencyKey
    ) {
        OrganizeRetryService.Result result = retryService.retry(roomId, memberId, jobId, idempotencyKey);
        ApiResponse<OrganizeJobResponse> body = ApiResponse.of(OrganizeJobResponse.from(result.job(), retryPolicy));
        return ResponseEntity.status(result.started() ? HttpStatus.ACCEPTED : HttpStatus.OK).body(body);
    }
}
