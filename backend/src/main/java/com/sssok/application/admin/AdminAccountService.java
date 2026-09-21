package com.sssok.application.admin;

import com.sssok.application.admin.exception.AdminNotFoundException;
import com.sssok.application.admin.exception.DuplicateAdminLoginIdException;
import com.sssok.application.admin.exception.LastSuperAdminException;
import com.sssok.application.port.out.AdminRepository;
import com.sssok.application.port.out.PasswordEncoder;
import com.sssok.common.exception.ErrorCode;
import com.sssok.common.exception.SssOkException;
import com.sssok.domain.admin.Admin;
import com.sssok.domain.admin.AdminName;
import com.sssok.domain.admin.AdminPermission;
import com.sssok.domain.admin.AdminRole;
import com.sssok.domain.admin.LoginId;
import com.sssok.domain.admin.exception.InvalidAdminPasswordException;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 조회는 모든 관리자가, 생성·수정·삭제는 계정 관리 권한을 가진 관리자만 할 수 있다.
@Service
@RequiredArgsConstructor
public class AdminAccountService {

    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final int MAX_PASSWORD_LENGTH = 72;

    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;
    private final AdminPermissionChecker permissionChecker;

    @Transactional(readOnly = true)
    public List<Admin> findAll(Long requesterId) {
        permissionChecker.require(requesterId, AdminPermission.FEEDBACK_READ);
        return adminRepository.findAllOrderByIdAsc();
    }

    @Transactional
    public Admin create(Long requesterId, String loginId, String rawPassword, String name, String role) {
        permissionChecker.require(requesterId, AdminPermission.ADMIN_ACCOUNT_MANAGE);

        LoginId newLoginId = new LoginId(loginId);
        requireValidPassword(rawPassword);
        AdminName adminName = new AdminName(name);
        AdminRole adminRole = role == null ? AdminRole.ADMIN : AdminRole.from(role);

        if (adminRepository.existsByLoginId(newLoginId.value())) {
            throw new DuplicateAdminLoginIdException();
        }
        try {
            return adminRepository.save(Admin.register(
                newLoginId, passwordEncoder.encode(rawPassword), adminName, adminRole, Instant.now()));
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateAdminLoginIdException();
        }
    }

    @Transactional
    public Admin update(Long requesterId, Long targetId, String name, String rawPassword, String role) {
        permissionChecker.require(requesterId, AdminPermission.ADMIN_ACCOUNT_MANAGE);
        if (name == null && rawPassword == null && role == null) {
            throw new EmptyPatchException();
        }

        Admin target = adminRepository.findById(targetId).orElseThrow(AdminNotFoundException::new);

        if (name != null) {
            target.rename(new AdminName(name));
        }
        if (rawPassword != null) {
            requireValidPassword(rawPassword);
            target.changePassword(passwordEncoder.encode(rawPassword));
        }
        if (role != null) {
            AdminRole newRole = AdminRole.from(role);
            if (target.isSuperAdmin() && newRole != AdminRole.SUPER_ADMIN) {
                requireNotLastSuperAdmin();
            }
            target.changeRole(newRole);
        }
        return adminRepository.save(target);
    }

    @Transactional
    public Long delete(Long requesterId, Long targetId) {
        permissionChecker.require(requesterId, AdminPermission.ADMIN_ACCOUNT_MANAGE);

        Admin target = adminRepository.findById(targetId).orElseThrow(AdminNotFoundException::new);
        if (target.isSuperAdmin()) {
            requireNotLastSuperAdmin();
        }
        adminRepository.deleteById(targetId);
        return targetId;
    }

    // 마지막 슈퍼관리자가 사라지면 계정을 만들거나 고칠 수 있는 사람이 아무도 없어진다.
    // 본인이 본인을 지우는 경우도 같은 규칙을 탄다.
    private void requireNotLastSuperAdmin() {
        if (adminRepository.countByRole(AdminRole.SUPER_ADMIN) <= 1) {
            throw new LastSuperAdminException();
        }
    }

    // BCrypt 는 72바이트를 넘는 입력을 잘라서 해싱한다. 그보다 긴 비밀번호를 받으면
    // 뒷부분이 사실상 무시되므로, 애초에 넘기지 못하게 막는다.
    private void requireValidPassword(String rawPassword) {
        if (rawPassword == null) {
            throw new InvalidAdminPasswordException();
        }
        int length = rawPassword.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        if (length < MIN_PASSWORD_LENGTH || length > MAX_PASSWORD_LENGTH) {
            throw new InvalidAdminPasswordException();
        }
    }

    private static final class EmptyPatchException extends SssOkException {

        private EmptyPatchException() {
            super(ErrorCode.EMPTY_PATCH);
        }
    }
}
