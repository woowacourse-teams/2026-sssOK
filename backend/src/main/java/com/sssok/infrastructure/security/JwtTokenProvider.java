package com.sssok.infrastructure.security;

import com.sssok.application.auth.exception.UnauthorizedException;
import com.sssok.application.port.out.TokenProvider;
import com.sssok.infrastructure.config.JwtProperties;
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
            String subject = Jwts.parser()
                .verifyWith(secretKey())
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
            return Long.valueOf(subject);
        } catch (JwtException | IllegalArgumentException e) {
            throw new UnauthorizedException("다시 접속해주세요");
        }
    }

    private SecretKey secretKey() {
        return Keys.hmacShaKeyFor(Base64.getDecoder().decode(jwtProperties.secret()));
    }
}
