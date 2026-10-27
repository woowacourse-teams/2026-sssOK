package com.sssok.renewal.incoming.media;

import com.sssok.renewal.application.media.upload.issue.IssueUploadUrlsCommand;
import com.sssok.renewal.application.media.upload.issue.UploadCandidate;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record IssueUploadUrlsRequest(

    @Schema(description = "업로드할 파일 목록")
    @NotEmpty(message = "업로드할 파일이 없습니다")
    List<@NotNull(message = "파일 정보가 올바르지 않습니다") UploadFileRequest> files,

    @Schema(description = "업로드와 동시에 담을 폴더 ID 목록. 생략하면 루트")
    List<@NotNull(message = "폴더 ID 목록이 올바르지 않습니다") Long> folderIds

) {
    public IssueUploadUrlsCommand toCommand(Long roomId, Long memberId) {
        List<UploadCandidate> uploadFiles = files.stream()
            .map(file -> new UploadCandidate(file.fileName(), file.mimeType(), file.size()))
            .toList();

        return new IssueUploadUrlsCommand(roomId, memberId, uploadFiles, folderIds);
    }

    public record UploadFileRequest(
        @Schema(description = "원본 파일명", example = "IMG_0421.jpg") String fileName,
        @Schema(description = "파일 MIME 타입", example = "image/jpeg") String mimeType,
        @Schema(description = "파일 바이트 크기", example = "3840219") Long size
    ) {
    }
}
