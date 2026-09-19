package com.sssok.application.admin;

import com.sssok.application.admin.exception.InvalidCredentialsException;
import com.sssok.application.admin.exception.InvalidLoginRequestException;
import com.sssok.application.port.out.AdminRepository;
import com.sssok.application.port.out.AdminTokenProvider;
import com.sssok.application.port.out.AdminTokenProvider.IssuedAdminToken;
import com.sssok.application.port.out.PasswordEncoder;
import com.sssok.domain.admin.Admin;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 관리자 로그인. 아이디·비밀번호를 확인하고 관리자 토큰을 발급한다.
@Service
@RequiredArgsConstructor
public class LoginAdminService {

    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;
    private final AdminTokenProvider adminTokenProvider;
    private final LoginAttemptLimiter loginAttemptLimiter;

    @Transactional(readOnly = true)
    public AdminAuthResult login(String loginId, String rawPassword) {
        requireFilled(loginId, rawPassword);
        Instant now = Instant.now();
        String normalizedLoginId = normalize(loginId);
        loginAttemptLimiter.requireNotLocked(normalizedLoginId, now);

        Optional<Admin> found = adminRepository.findByLoginId(normalizedLoginId);
        // 아이디가 없어도 비밀번호 대조를 건너뛰지 않는다. 없는 아이디일 때만 빨리 응답하면
        // 응답 시간 차이로 어떤 아이디가 존재하는지 알아낼 수 있다.
        String passwordHash = found.map(Admin::getPasswordHash).orElse(null);
        boolean matched = passwordEncoder.matches(rawPassword, passwordHash);

        if (found.isEmpty() || !matched) {
            loginAttemptLimiter.recordFailure(normalizedLoginId, now);
            throw new InvalidCredentialsException();
        }

        loginAttemptLimiter.reset(normalizedLoginId);
        Admin admin = found.get();
        IssuedAdminToken token = adminTokenProvider.issue(admin.getId(), admin.getRole(), now);
        return AdminAuthResult.of(admin, token.value(), token.expiresAt());
    }

    private void requireFilled(String loginId, String rawPassword) {
        if (loginId == null || loginId.isBlank() || rawPassword == null || rawPassword.isBlank()) {
            throw new InvalidLoginRequestException();
        }
    }

    private String normalize(String loginId) {
        return loginId.strip().toLowerCase();
    }
}
