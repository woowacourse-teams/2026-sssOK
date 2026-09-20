package com.sssok.application.admin;

import com.sssok.domain.admin.Admin;
import com.sssok.domain.admin.AdminRole;
import java.time.Instant;

public record AdminAuthResult(
    String accessToken, Long adminId, String loginId, String name, AdminRole role, Instant expiresAt
) {

    public static AdminAuthResult of(Admin admin, String accessToken, Instant expiresAt) {
        return new AdminAuthResult(
            accessToken,
            admin.getId(),
            admin.getLoginId().value(),
            admin.getName().value(),
            admin.getRole(),
            expiresAt
        );
    }
}
