package com.sssok.renewal.incoming.auth;

import com.sssok.renewal.domain.auth.RefreshTokenDelivery;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// 토큰은 Authorization 헤더로 제공자에게 전달되므로 공백·제어 문자 등 헤더 주입이 가능한 문자를 거절한다.
public record KakaoNativeLoginRequest(
    @NotBlank @Size(max = 2048) @Pattern(regexp = "[\\x21-\\x7E]+") String kakaoAccessToken,
    RefreshTokenDelivery refreshTokenDelivery
) {
    public RefreshTokenDelivery refreshTokenDelivery() {
        return RefreshTokenDelivery.orDefault(refreshTokenDelivery);
    }

    @Override
    public String toString() { return "KakaoNativeLoginRequest[REDACTED]"; }
}
