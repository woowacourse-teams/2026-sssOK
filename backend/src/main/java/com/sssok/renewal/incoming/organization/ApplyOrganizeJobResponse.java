package com.sssok.renewal.incoming.organization;

import com.sssok.renewal.application.organization.job.OrganizeApplyService;
import com.sssok.renewal.domain.organization.OrganizeJobStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

@Schema(description = "폴더 반영 결과")
public record ApplyOrganizeJobResponse(
    UUID jobId,
    OrganizeJobStatus status,
    List<Folder> folders
) {
    public static ApplyOrganizeJobResponse from(OrganizeApplyService.Applied applied) {
        List<Folder> folders = applied.folders().stream()
            .map(folder -> new Folder(folder.folderId(), folder.folderName(), folder.mediaIds()))
            .toList();
        return new ApplyOrganizeJobResponse(applied.job().publicId(), applied.job().status(), folders);
    }

    public record Folder(Long folderId, String folderName, List<Long> mediaIds) {
    }
}
