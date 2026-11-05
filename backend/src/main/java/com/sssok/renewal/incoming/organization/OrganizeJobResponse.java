package com.sssok.renewal.incoming.organization;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.sssok.renewal.application.organization.job.OrganizeRetryPolicy;
import com.sssok.renewal.domain.organization.OrganizeJob;
import com.sssok.renewal.domain.organization.OrganizeJobStatus;
import com.sssok.renewal.domain.organization.OrganizeKind;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "사진 정리 작업 요약")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OrganizeJobResponse(
    UUID jobId,
    OrganizeJobStatus status,
    OrganizeKind kind,
    int photoCount,
    @Schema(description = "분석에서 제외한 사진 수(동영상, GIF, 처리 중)") int excludedCount,
    @Schema(description = "실행 회차. 전송만 다시 보낼 때는 늘지 않는다") int attempt,
    @Schema(description = "작업이 바뀔 때마다 늘어나는 버전. 오래된 SSE·스냅샷을 가리는 데 쓴다") int jobVersion,
    Progress progress,
    @Schema(description = "FAILED 일 때의 사유 코드") String failureCode,
    @Schema(description = "지금 다시 시도할 의미가 있는지. 호출 때 서버가 다시 판단한다") boolean retryable,
    @JsonInclude(JsonInclude.Include.ALWAYS) @Schema(description = "다시 시도할 수 있는 시각. 없으면 null")
    Instant retryAvailableAt,
    @JsonInclude(JsonInclude.Include.ALWAYS) @Schema(description = "검토 만료 시각(분석 완료 +30분). READY 전에는 null")
    Instant reviewExpiresAt,
    @JsonInclude(JsonInclude.Include.ALWAYS) @Schema(description = "결과 보관 만료 시각(분석 완료 +3일). 결과가 없으면 null")
    Instant resultExpiresAt,
    Instant createdAt,
    Instant updatedAt
) {
    public record Progress(String stage, Integer processed, Integer total) {
    }

    public static Progress progressOf(OrganizeJob job) {
        boolean hasProgress = job.progressStage() != null || job.progressProcessed() != null;
        return hasProgress ? new Progress(job.progressStage(), job.progressProcessed(), job.progressTotal()) : null;
    }

    public static OrganizeJobResponse from(OrganizeJob job, OrganizeRetryPolicy retryPolicy) {
        return new OrganizeJobResponse(job.publicId(), job.status(), job.kind(), job.photoCount(),
            job.excludedCount(), job.retryCount() + 1, job.jobVersion(), progressOf(job), job.failureCode(),
            retryPolicy.retryable(job), retryPolicy.retryAvailableAt(job), job.reviewExpiresAt(),
            job.resultExpiresAt(), job.createdAt(), job.updatedAt());
    }
}
