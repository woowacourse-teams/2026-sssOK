package com.sssok.presentation.api.auth;

import com.sssok.application.auth.LinkCodeResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

public record LinkCodeResponse(
    @Schema(description = "다른 기기 화면에 보여주고 입력받을 6자리 코드", example = "483920") String linkCode,
    @Schema(description = "이 코드의 만료 시각") Instant expiresAt
) {

    public static LinkCodeResponse from(LinkCodeResult result) {
        return new LinkCodeResponse(result.linkCode(), result.expiresAt());
    }
}
