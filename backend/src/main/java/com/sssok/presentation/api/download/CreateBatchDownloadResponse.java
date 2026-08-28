package com.sssok.presentation.api.download;

import com.sssok.application.download.BatchDownloadFile;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record CreateBatchDownloadResponse(
    @Schema(description = "다운로드 대상 파일 목록") List<BatchDownloadFileResponse> files
) {

    public static CreateBatchDownloadResponse from(List<BatchDownloadFile> files) {
        return new CreateBatchDownloadResponse(files.stream().map(BatchDownloadFileResponse::from).toList());
    }
}
