package com.sssok.presentation.api.admin;

import io.swagger.v3.oas.annotations.media.Schema;

public record UpdateAdminRequest(
    @Schema(description = "바꿀 표시 이름") String name,
    @Schema(description = "바꿀 비밀번호. 8~72바이트") String password,
    @Schema(description = "SUPER_ADMIN 또는 ADMIN") String role
) {
}
