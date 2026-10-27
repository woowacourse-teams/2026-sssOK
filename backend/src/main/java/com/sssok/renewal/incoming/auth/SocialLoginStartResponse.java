package com.sssok.renewal.incoming.auth;

import com.sssok.renewal.application.auth.SocialLoginStartResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

public record SocialLoginStartResponse(
    @Schema(description = "소셜 로그인을 진행할 URL") String authorizationUrl,
    @Schema(description = "콜백에서 돌아오는 값과 연결할 로그인 요청 식별값") String state,
    @Schema(description = "시작한 클라이언트에 임시 보관하고 완료 API에만 전달할 확인값. URL에 포함하지 않는다") String loginVerifier,
    @Schema(description = "로그인 요청 만료 시각") Instant expiresAt
) {
    public static SocialLoginStartResponse from(SocialLoginStartResult result) {
        return new SocialLoginStartResponse(result.authorizationUrl(), result.state(), result.loginVerifier(), result.expiresAt());
    }

    @Override
    public String toString() {
        return "SocialLoginStartResponse[REDACTED]";
    }
}
