package com.sssok.presentation.api.admin;

import com.sssok.application.admin.AdminAuthResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

public record AdminLoginResponse(
    @Schema(description = "이후 관리자 API 요청의 Authorization: Bearer {accessToken} 에 담을 토큰")
    String accessToken,
    @Schema(description = "로그인한 관리자의 ID") Long adminId,
    @Schema(description = "로그인한 관리자의 아이디") String loginId,
    @Schema(description = "관리자 표시 이름") String name,
    @Schema(description = "SUPER_ADMIN 또는 ADMIN") String role,
    @Schema(description = "토큰 만료 시각") Instant expiresAt
) {

    public static AdminLoginResponse from(AdminAuthResult result) {
        return new AdminLoginResponse(
            result.accessToken(),
            result.adminId(),
            result.loginId(),
            result.name(),
            result.role().name(),
            result.expiresAt()
        );
    }
}
