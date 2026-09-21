package com.sssok.presentation.api.admin;

import com.sssok.domain.admin.Admin;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

public record AdminAccountResponse(
    @Schema(description = "관리자 ID") Long adminId,
    @Schema(description = "로그인 아이디") String loginId,
    @Schema(description = "표시 이름") String name,
    @Schema(description = "SUPER_ADMIN 또는 ADMIN") String role,
    @Schema(description = "계정 생성 시각") Instant createdAt
) {

    public static AdminAccountResponse from(Admin admin) {
        return new AdminAccountResponse(
            admin.getId(),
            admin.getLoginId().value(),
            admin.getName().value(),
            admin.getRole().name(),
            admin.getCreatedAt()
        );
    }
}
