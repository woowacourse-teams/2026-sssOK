package com.sssok.infrastructure.security;

import com.sssok.application.auth.exception.UnauthorizedException;
import com.sssok.application.port.out.AdminTokenProvider;
import com.sssok.domain.admin.AdminRole;
import com.sssok.infrastructure.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import javax.crypto.SecretKey;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// 회원 토큰과 같은 키로 서명하되 주체 구분 클레임으로 갈라 둔다.
@Component
@RequiredArgsConstructor
public class JwtAdminTokenProvider implements AdminTokenProvider {

    public static final String SUBJECT_TYPE_CLAIM = "typ";
    public static final String ADMIN_SUBJECT_TYPE = "admin";
    private static final String ROLE_CLAIM = "role";

    private final JwtProperties jwtProperties;

    // 관리자는 권한이 세서 회원 토큰보다 짧게 잡는다.
    @Value("${admin.token-ttl:1d}")
    private java.time.Duration tokenTtl;

    @Override
    public IssuedAdminToken issue(Long adminId, AdminRole role, Instant now) {
        Instant expiresAt = now.plus(tokenTtl);
        String token = Jwts.builder()
            .subject(String.valueOf(adminId))
            .claim(SUBJECT_TYPE_CLAIM, ADMIN_SUBJECT_TYPE)
            .claim(ROLE_CLAIM, role.name())
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiresAt))
            .signWith(secretKey())
            .compact();
        return new IssuedAdminToken(token, expiresAt);
    }

    @Override
    public Long parseAdminId(String token) {
        try {
            Claims claims = Jwts.parser()
                .verifyWith(secretKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
            if (!ADMIN_SUBJECT_TYPE.equals(claims.get(SUBJECT_TYPE_CLAIM, String.class))) {
                throw new UnauthorizedException("다시 로그인해주세요");
            }
            return Long.valueOf(claims.getSubject());
        } catch (JwtException | IllegalArgumentException e) {
            throw new UnauthorizedException("다시 로그인해주세요");
        }
    }

    private SecretKey secretKey() {
        return Keys.hmacShaKeyFor(Base64.getDecoder().decode(jwtProperties.secret()));
    }
}
