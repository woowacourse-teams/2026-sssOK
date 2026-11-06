package com.sssok.renewal.incoming.auth;

import com.sssok.renewal.application.auth.OAuthLoginAttemptResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

public record AppleNativeStartResponse(
    @Schema(description = "서버 로그인 시도 식별값. 완료 API에 그대로 전달한다") String state,
    @Schema(description = "웹뷰가 보관하고 완료 API에만 전달할 확인값. 앱 SDK에는 전달하지 않는다") String loginVerifier,
    @Schema(description = "애플 SDK 인증 요청의 nonce에 변환 없이 그대로 넣을 값") String nonce,
    @Schema(description = "로그인 시도 만료 시각") Instant expiresAt
) {
    public static AppleNativeStartResponse from(OAuthLoginAttemptResult result) {
        return new AppleNativeStartResponse(result.state(), result.loginVerifier(), result.nonce(), result.expiresAt());
    }

    @Override
    public String toString() { return "AppleNativeStartResponse[REDACTED]"; }
}
