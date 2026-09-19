package com.sssok.infrastructure.security;

import com.sssok.application.auth.exception.UnauthorizedException;
import com.sssok.application.port.out.TokenProvider;
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
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JwtTokenProvider implements TokenProvider {

    private final JwtProperties jwtProperties;

    @Override
    public IssuedToken issue(Long memberId, Instant now) {
        Instant expiresAt = now.plus(jwtProperties.accessTokenTtl());
        String token = Jwts.builder()
            .subject(String.valueOf(memberId))
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiresAt))
            .signWith(secretKey())
            .compact();
        return new IssuedToken(token, expiresAt);
    }

    @Override
    public Long parse(String token) {
        try {
            Claims claims = Jwts.parser()
                .verifyWith(secretKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
            requireNotAdminToken(claims);
            return Long.valueOf(claims.getSubject());
        } catch (JwtException | IllegalArgumentException e) {
            throw new UnauthorizedException("다시 접속해주세요");
        }
    }

    // 관리자 토큰과 회원 토큰은 같은 키로 서명하므로 서명 검증만으로는 구분되지 않는다.
    // 회원 토큰에는 이 클레임이 없어서, 없으면 회원으로 본다.
    private void requireNotAdminToken(Claims claims) {
        String subjectType = claims.get(JwtAdminTokenProvider.SUBJECT_TYPE_CLAIM, String.class);
        if (JwtAdminTokenProvider.ADMIN_SUBJECT_TYPE.equals(subjectType)) {
            throw new UnauthorizedException("다시 접속해주세요");
        }
    }

    private SecretKey secretKey() {
        return Keys.hmacShaKeyFor(Base64.getDecoder().decode(jwtProperties.secret()));
    }
}
