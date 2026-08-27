package com.sssok.presentation.api.media;

import io.swagger.v3.oas.annotations.media.Schema;

public record ReissueUploadUrlRequest(
    @Schema(description = "재압축해서 크기가 바뀐 경우에만 보낸다. 생략하면 최초 발급 값을 그대로 쓴다")
    Long size
) {
}
