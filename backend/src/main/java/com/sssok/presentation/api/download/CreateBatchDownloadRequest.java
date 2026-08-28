package com.sssok.presentation.api.download;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record CreateBatchDownloadRequest(
    @Schema(description = "다운로드할 미디어 ID 목록 (최대 1000개). folderId와 함께 쓸 수 없다") List<Long> mediaIds,
    @Schema(description = "폴더 전체를 다운로드할 때 사용. mediaIds와 함께 쓸 수 없다") Long folderId
) {
}
