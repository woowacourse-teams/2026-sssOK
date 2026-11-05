package com.sssok.renewal.incoming.organization;

import com.sssok.renewal.application.organization.job.OrganizeApplyService;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "검토한 그룹을 폴더로 반영하는 요청. 일반 그룹(groupId 가 있는 그룹)만 반영할 수 있다")
public record ApplyOrganizeJobRequest(
    @Schema(description = "반영할 그룹. 1개 이상") List<Group> groups
) {
    // 형식 검증은 서비스가 한다. 비어 있거나 null 인 항목도 그대로 넘겨 서비스가 400 으로 거절하게 한다.
    public List<OrganizeApplyService.GroupRequest> toGroupRequests() {
        if (groups == null) {
            return null;
        }
        return groups.stream()
            .map(group -> group == null ? null
                : new OrganizeApplyService.GroupRequest(group.groupId(), group.folderName(), group.mediaIds()))
            .toList();
    }

    public record Group(
        @Schema(description = "결과의 일반 그룹 ID", example = "person_0001") String groupId,
        @Schema(description = "만들 폴더 이름. 기존 폴더 이름 규칙을 따르고 같은 이름이 있으면 거절한다", example = "민찬")
        String folderName,
        @Schema(description = "폴더에 담을 사진 ID. 해당 그룹의 사진이어야 한다") List<Long> mediaIds
    ) {
    }
}
