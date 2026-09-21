package com.sssok.domain.admin;

import com.sssok.domain.admin.exception.InvalidAdminRoleException;
import java.util.Arrays;
import java.util.Set;

public enum AdminRole {

    SUPER_ADMIN(Set.of(AdminPermission.FEEDBACK_READ, AdminPermission.ADMIN_ACCOUNT_MANAGE)),
    ADMIN(Set.of(AdminPermission.FEEDBACK_READ));

    private final Set<AdminPermission> permissions;

    AdminRole(Set<AdminPermission> permissions) {
        this.permissions = permissions;
    }

    public static AdminRole from(String name) {
        return Arrays.stream(values())
            .filter(role -> role.name().equals(name))
            .findFirst()
            .orElseThrow(() -> new InvalidAdminRoleException(name));
    }

    public boolean has(AdminPermission permission) {
        return permissions.contains(permission);
    }
}
