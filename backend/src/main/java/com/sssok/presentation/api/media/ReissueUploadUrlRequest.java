package com.sssok.presentation.api.media;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Positive;

public record ReissueUploadUrlRequest(
    @Schema(description = "재압축해서 크기가 바뀐 경우에만 보낸다. 생략하면 최초 발급 값을 그대로 쓴다")
    @Positive(message = "파일 크기가 올바르지 않습니다")
    Long size
) {
}
