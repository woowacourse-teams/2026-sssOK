package com.sssok.presentation.api.admin;

import io.swagger.v3.oas.annotations.media.Schema;

public record AdminLoginRequest(
    @Schema(description = "관리자 아이디", example = "superadmin") String loginId,
    @Schema(description = "관리자 비밀번호") String password
) {
}
