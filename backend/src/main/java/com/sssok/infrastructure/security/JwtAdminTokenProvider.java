package com.sssok.infrastructure.security;

import com.sssok.application.auth.exception.UnauthorizedException;
import com.sssok.application.port.out.AdminTokenProvider;
import com.sssok.infrastructure.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// 회원 토큰과 같은 키로 서명하되 주체 구분 클레임으로 갈라 둔다.
//
// 역할은 토큰에 담지 않는다.
@Component
public class JwtAdminTokenProvider implements AdminTokenProvider {

    private final JwtProperties jwtProperties;
    private final Duration tokenTtl;

    // 관리자는 권한이 세서 회원 토큰보다 짧게 잡는다.
    public JwtAdminTokenProvider(
        JwtProperties jwtProperties,
        @Value("${admin.token-ttl:1d}") Duration tokenTtl
    ) {
        this.jwtProperties = jwtProperties;
        this.tokenTtl = tokenTtl;
    }

    @Override
    public IssuedAdminToken issue(Long adminId, Instant now) {
        Instant expiresAt = now.plus(tokenTtl);
        String token = Jwts.builder()
            .subject(String.valueOf(adminId))
            .claim(TokenSubjectType.CLAIM_NAME, TokenSubjectType.ADMIN.claimValue())
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
            requireAdminSubject(claims);
            return Long.valueOf(claims.getSubject());
        } catch (JwtException | IllegalArgumentException e) {
            throw new UnauthorizedException("다시 로그인해주세요");
        }
    }

    // 관리자 토큰만 통과시킨다. 회원 토큰, 모르는 타입, 타입 클레임이 없는 토큰 모두 거부한다
    private void requireAdminSubject(Claims claims) {
        String claimValue = claims.get(TokenSubjectType.CLAIM_NAME, String.class);
        if (TokenSubjectType.from(claimValue).orElse(null) != TokenSubjectType.ADMIN) {
            throw new UnauthorizedException("다시 로그인해주세요");
        }
    }

    private SecretKey secretKey() {
        return Keys.hmacShaKeyFor(Base64.getDecoder().decode(jwtProperties.secret()));
    }
}
