package com.sssok.renewal.incoming.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record SocialLoginStartRequest(
    @Size(max = 100) List<@NotBlank @Size(max = 4096) String> anonymousAccessTokens
) {
    public SocialLoginStartRequest {
        anonymousAccessTokens = anonymousAccessTokens == null ? List.of() : List.copyOf(anonymousAccessTokens);
    }

    @Override
    public String toString() { return "SocialLoginStartRequest[REDACTED]"; }
}
