package com.sssok.presentation.api.auth;

import com.sssok.application.auth.AuthResult;
import java.time.Instant;

public record AuthResponse(String accessToken, Long userId, String nickname, Instant expiresAt) {

    public static AuthResponse from(AuthResult result) {
        return new AuthResponse(result.accessToken(), result.userId(), result.nickname(), result.expiresAt());
    }
}
