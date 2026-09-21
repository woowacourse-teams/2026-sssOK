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
            .claim(TokenSubjectType.CLAIM_NAME, TokenSubjectType.MEMBER.claimValue())
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
            requireMemberSubject(claims);
            return Long.valueOf(claims.getSubject());
        } catch (JwtException | IllegalArgumentException e) {
            throw new UnauthorizedException("다시 접속해주세요");
        }
    }

    // 회원 토큰만 통과시킨다. 관리자 토큰은 물론이고 모르는 타입도 거부한다.
    //
    // 타입 클레임이 없는 토큰은 이 클레임을 담기 전에 발급된 회원 토큰이라서 한시적으로 받아 준다.
    // 바로 거부하면 이미 로그인해 둔 사용자가 전부 튕긴다.
    private void requireMemberSubject(Claims claims) {
        String claimValue = claims.get(TokenSubjectType.CLAIM_NAME, String.class);
        if (claimValue == null) {
            return;
        }
        if (TokenSubjectType.from(claimValue).orElse(null) != TokenSubjectType.MEMBER) {
            throw new UnauthorizedException("다시 접속해주세요");
        }
    }

    private SecretKey secretKey() {
        return Keys.hmacShaKeyFor(Base64.getDecoder().decode(jwtProperties.secret()));
    }
}
