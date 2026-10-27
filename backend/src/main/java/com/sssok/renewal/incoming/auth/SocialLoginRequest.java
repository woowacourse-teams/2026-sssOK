package com.sssok.renewal.incoming.auth;

import com.sssok.renewal.domain.auth.RefreshTokenDelivery;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SocialLoginRequest(
    @NotBlank @Size(max = 2048) String code,
    @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{43}") String state,
    @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{43}") String loginVerifier,
    RefreshTokenDelivery refreshTokenDelivery
) {
    public RefreshTokenDelivery refreshTokenDelivery() {
        return RefreshTokenDelivery.orDefault(refreshTokenDelivery);
    }

    @Override
    public String toString() { return "SocialLoginRequest[REDACTED]"; }
}
