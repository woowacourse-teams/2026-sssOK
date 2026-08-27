package com.sssok.presentation.api.media;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record IssueUploadUrlsRequest(
    @Schema(description = "업로드할 파일 목록") List<UploadFileRequest> files,
    @Schema(description = "업로드와 동시에 담을 폴더 ID 목록. 생략하면 루트") List<Long> folderIds
) {
    public record UploadFileRequest(
        @Schema(description = "원본 파일명", example = "IMG_0421.jpg") String fileName,
        @Schema(description = "파일 MIME 타입", example = "image/jpeg") String mimeType,
        @Schema(description = "파일 바이트 크기", example = "3840219") Long size
    ) {
    }
}
