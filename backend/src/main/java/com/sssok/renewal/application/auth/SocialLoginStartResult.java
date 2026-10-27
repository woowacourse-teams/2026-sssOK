package com.sssok.renewal.application.auth;

import java.time.Instant;

public record SocialLoginStartResult(String authorizationUrl, String state, String loginVerifier, Instant expiresAt) {
    @Override
    public String toString() {
        return "SocialLoginStartResult[REDACTED]";
    }
}
