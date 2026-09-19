package com.sssok.infrastructure.security;

import com.sssok.application.port.out.PasswordEncoder;
import org.springframework.stereotype.Component;

// salt 는 해시 문자열 안에 함께 들어가므로 따로 보관하지 않는다.
@Component
public class BCryptPasswordEncoderAdapter implements PasswordEncoder {

    // 해시가 없을 때 대조에 쓰는 더미. 존재하지 않는 아이디로 로그인해도 실제와 같은 비용의
    // 해시 연산을 한 번 치르게 해서, 응답 시간 차이로 아이디 존재 여부가 드러나지 않게 한다.
    private static final String DUMMY_HASH =
        "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    private final org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder delegate =
        new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();

    @Override
    public String encode(String rawPassword) {
        return delegate.encode(rawPassword);
    }

    @Override
    public boolean matches(String rawPassword, String passwordHash) {
        if (rawPassword == null) {
            return false;
        }
        if (passwordHash == null) {
            delegate.matches(rawPassword, DUMMY_HASH);
            return false;
        }
        return delegate.matches(rawPassword, passwordHash);
    }
}
