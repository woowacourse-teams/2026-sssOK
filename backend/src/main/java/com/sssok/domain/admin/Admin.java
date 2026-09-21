package com.sssok.domain.admin;

import java.time.Instant;
import lombok.Getter;

// 익명 회원(Member)과 달리 방에 매이지 않고 계속 유지되는 계정.
// 비밀번호는 해시만 들고 있다
@Getter
public class Admin {

    private final Long id;
    private final LoginId loginId;
    private String passwordHash;
    private AdminName name;
    private AdminRole role;
    private final Instant createdAt;

    private Admin(
        Long id, LoginId loginId, String passwordHash, AdminName name, AdminRole role, Instant createdAt
    ) {
        this.id = id;
        this.loginId = loginId;
        this.passwordHash = passwordHash;
        this.name = name;
        this.role = role;
        this.createdAt = createdAt;
    }

    public static Admin register(
        LoginId loginId, String passwordHash, AdminName name, AdminRole role, Instant now
    ) {
        return new Admin(null, loginId, passwordHash, name, role, now);
    }

    public static Admin reconstruct(
        Long id, LoginId loginId, String passwordHash, AdminName name, AdminRole role, Instant createdAt
    ) {
        return new Admin(id, loginId, passwordHash, name, role, createdAt);
    }

    public void rename(AdminName newName) {
        this.name = newName;
    }

    public void changePassword(String newPasswordHash) {
        this.passwordHash = newPasswordHash;
    }

    public void changeRole(AdminRole newRole) {
        this.role = newRole;
    }

    public boolean can(AdminPermission permission) {
        return role.has(permission);
    }

    public boolean isSuperAdmin() {
        return role == AdminRole.SUPER_ADMIN;
    }

    public boolean isSame(Long adminId) {
        return id != null && id.equals(adminId);
    }
}
