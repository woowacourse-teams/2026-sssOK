package com.sssok.application.auth;

import java.time.Instant;

public record AuthResult(String accessToken, Long userId, String nickname, Instant expiresAt) {
}
