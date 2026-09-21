package com.sssok.presentation.api.download;

import io.swagger.v3.oas.annotations.media.Schema;
import com.sssok.presentation.api.common.MediaSelectionRequest;

public record CreateDownloadJobRequest(
    @Schema(description = "압축할 미디어 선택 범위 (실제 대상 최대 1000개). folderId와 함께 쓸 수 없다")
    MediaSelectionRequest selection,
    @Schema(description = "폴더 전체를 압축할 때 사용. selection과 함께 쓸 수 없다") Long folderId
) {
}
