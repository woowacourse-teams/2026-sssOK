package com.sssok.renewal.incoming.organization;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.sssok.renewal.application.organization.job.OrganizeJobQueryService;
import com.sssok.renewal.application.organization.job.OrganizeRetryPolicy;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "화면 재진입 스냅샷. 방이 사용 중인지와 요청자 본인의 작업을 함께 돌려준다")
@JsonInclude(JsonInclude.Include.ALWAYS)
public record OrganizeCurrentResponse(
    @Schema(description = "방에 활성 작업(QUEUED·RUNNING·READY)이 있는지. 누구의 작업인지와 무관하다") boolean roomBusy,
    @Schema(description = "방 점유 상태가 바뀔 때마다 늘어나는 버전") long roomVersion,
    @Schema(description = "요청자 본인의 작업. 활성 작업을 우선하고 없으면 최근 3일 안에 끝난 작업 1개, 없으면 null")
    OrganizeJobResponse myJob,
    @Schema(description = "익명 회원의 이 방 체험 가능 여부. true: 사용 가능, false: 사용 중 또는 소진, "
        + "가입 회원은 null. 방 사용 여부·접수 가능 여부와 별개이며 실제 접수 시 다시 검사한다", nullable = true)
    Boolean anonymousTrialAvailable
) {
    public static OrganizeCurrentResponse from(OrganizeJobQueryService.Current current,
                                               OrganizeRetryPolicy retryPolicy) {
        OrganizeJobResponse myJob = current.myJob() == null ? null
            : OrganizeJobResponse.from(current.myJob(), retryPolicy);
        return new OrganizeCurrentResponse(current.roomBusy(), current.roomVersion(), myJob,
            current.anonymousTrialAvailable());
    }
}
