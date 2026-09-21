package com.sssok.application.admin;

import com.sssok.application.admin.exception.AdminForbiddenException;
import com.sssok.application.admin.exception.SuperAdminRequiredException;
import com.sssok.application.port.out.AdminRepository;
import com.sssok.domain.admin.Admin;
import com.sssok.domain.admin.AdminPermission;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// 인가 판정을 한곳에 모은다. 호출하는 쪽은 "이 권한이 필요하다"만 말하고 역할을 직접 비교하지 않는다.
//
// 역할은 토큰에서 읽지 않고 매번 계정을 다시 읽는다. 토큰을 무효화할 수단이 없어서 강등되거나
// 삭제된 관리자의 옛 토큰이 만료 전까지 살아 있기 때문이다. 그래서 토큰에도 역할을 담지 않는다.
@Component
@RequiredArgsConstructor
public class AdminPermissionChecker {

    private final AdminRepository adminRepository;

    @Transactional(readOnly = true)
    public Admin require(Long adminId, AdminPermission permission) {
        Admin admin = adminRepository.findById(adminId).orElseThrow(AdminForbiddenException::new);
        if (!admin.can(permission)) {
            throw deniedFor(permission);
        }
        return admin;
    }

    // 권한별로 응답을 갈라 준다. 계정 관리는 슈퍼관리자만 가능하다는 사실 자체가 팀 내부에 공개된
    // 규칙이라 알려줘도 되고, 알려줘야 막힌 사람이 누구에게 요청할지 안다.
    private RuntimeException deniedFor(AdminPermission permission) {
        if (permission == AdminPermission.ADMIN_ACCOUNT_MANAGE) {
            return new SuperAdminRequiredException();
        }
        return new AdminForbiddenException();
    }
}
