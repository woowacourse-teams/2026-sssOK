package com.sssok.renewal.incoming.organization;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "문장으로 정리 시작")
public record StartOrganizeJobByTextRequest(
    @Schema(description = "1~1000자 요청 문장") String text
) {
}
