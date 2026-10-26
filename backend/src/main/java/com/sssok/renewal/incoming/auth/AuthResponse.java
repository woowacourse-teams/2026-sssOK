package com.sssok.renewal.incoming.auth;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.sssok.renewal.application.auth.AuthResult;
import com.sssok.renewal.domain.auth.RefreshTokenDelivery;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

public record AuthResponse(
    @Schema(description = "이후 모든 요청의 Authorization: Bearer {accessToken} 헤더에 실어 보낼 토큰") String accessToken,
    @Schema(description = "발급된 계정의 식별자") Long userId,
    @Schema(description = "발급된 계정의 닉네임", example = "민수") String nickname,
    @Schema(description = "accessToken 만료 시각") Instant expiresAt,
    @JsonInclude(JsonInclude.Include.NON_NULL) String refreshToken,
    Instant refreshExpiresAt
) {

    public static AuthResponse from(AuthResult result, RefreshTokenDelivery delivery) {
        return new AuthResponse(result.accessToken(), result.userId(), result.nickname(), result.expiresAt(),
            delivery == RefreshTokenDelivery.BODY ? result.refreshToken() : null, result.refreshExpiresAt());
    }
    @Override
    public String toString() { return "AuthResponse[REDACTED]"; }
}
