package com.sssok.renewal.incoming.organization;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.sssok.presentation.api.media.MediaResponse;
import com.sssok.renewal.application.organization.job.OrganizeResultView;
import com.sssok.renewal.domain.organization.OrganizeKind;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

@Schema(description = "검토용 결과. 사진 항목은 갤러리 목록 항목과 같다")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OrganizeResultResponse(
    UUID jobId,
    OrganizeKind kind,
    int resultVersion,
    int photoCount,
    @Schema(description = "검토 중 삭제되어 응답에서 뺀 사진 수") int removedCount,
    List<Group> groups,
    @Schema(description = "SIMILAR 에만 있다. 2장짜리 후보") List<Group> smallGroups,
    Group unassigned,
    List<ErrorItem> errors
) {
    public record Group(String groupId, int photoCount, List<MediaResponse> photos) {
    }

    public record ErrorItem(long mediaId, String code) {
    }

    public static OrganizeResultResponse from(OrganizeResultView view) {
        return new OrganizeResultResponse(view.jobId(), view.kind(), view.resultVersion(), view.photoCount(),
            view.removedCount(), view.groups().stream().map(OrganizeResultResponse::group).toList(),
            view.smallGroups() == null ? null : view.smallGroups().stream().map(OrganizeResultResponse::group).toList(),
            group(view.unassigned()),
            view.errors().stream().map(error -> new ErrorItem(error.mediaId(), error.code())).toList());
    }

    private static Group group(OrganizeResultView.Group group) {
        return new Group(group.groupId(), group.photoCount(),
            group.photos().stream().map(MediaResponse::from).toList());
    }
}
