package com.sssok.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sssok.application.auth.exception.UnauthorizedException;
import com.sssok.application.port.out.AdminTokenProvider.IssuedAdminToken;
import com.sssok.application.port.out.TokenProvider.IssuedToken;
import com.sssok.infrastructure.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;

// 두 토큰은 같은 키로 서명하므로 주체 구분은 클레임만으로 이뤄진다. 그 경계가 양방향으로
// 막히는지, 그리고 알 수 없는 주체가 어느 쪽으로도 새지 않는지 확인한다.
class TokenSubjectTypeTest {

    private static final String SECRET = "mvtcAhMGbXlhpJ4JuIvRWJVF+yMUju6PxRCb/FMojgA=";
    private static final Instant NOW = Instant.now();

    private final JwtProperties jwtProperties = new JwtProperties(SECRET, Duration.ofDays(30));
    private final JwtTokenProvider tokenProvider = new JwtTokenProvider(jwtProperties);
    private final JwtAdminTokenProvider adminTokenProvider =
        new JwtAdminTokenProvider(jwtProperties, Duration.ofDays(1));

    @Test
    void 회원_토큰은_회원_파서로_읽힌다() {
        IssuedToken issued = tokenProvider.issue(42L, NOW);

        assertThat(tokenProvider.parse(issued.value())).isEqualTo(42L);
    }

    @Test
    void 관리자_토큰은_관리자_파서로_읽힌다() {
        IssuedAdminToken issued = adminTokenProvider.issue(7L, NOW);

        assertThat(adminTokenProvider.parseAdminId(issued.value())).isEqualTo(7L);
    }

    @Test
    void 관리자_토큰은_회원_파서가_거부한다() {
        String adminToken = adminTokenProvider.issue(7L, NOW).value();

        assertThatThrownBy(() -> tokenProvider.parse(adminToken))
            .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void 회원_토큰은_관리자_파서가_거부한다() {
        String memberToken = tokenProvider.issue(42L, NOW).value();

        assertThatThrownBy(() -> adminTokenProvider.parseAdminId(memberToken))
            .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void 알_수_없는_주체_타입은_양쪽_파서가_모두_거부한다() {
        String unknownToken = 토큰("robot", 42L);

        assertThatThrownBy(() -> tokenProvider.parse(unknownToken))
            .isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(() -> adminTokenProvider.parseAdminId(unknownToken))
            .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void 타입_클레임이_없는_토큰은_레거시_회원_토큰으로만_받아_준다() {
        String legacyToken = 토큰(null, 42L);

        assertThat(tokenProvider.parse(legacyToken)).isEqualTo(42L);
        assertThatThrownBy(() -> adminTokenProvider.parseAdminId(legacyToken))
            .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void 회원_토큰에는_회원_주체_타입이_담긴다() {
        String memberToken = tokenProvider.issue(42L, NOW).value();

        assertThat(클레임(memberToken).get(TokenSubjectType.CLAIM_NAME)).isEqualTo("member");
    }

    @Test
    void 관리자_토큰에는_주체_타입만_담기고_역할_클레임은_없다() {
        String adminToken = adminTokenProvider.issue(7L, NOW).value();

        Claims claims = 클레임(adminToken);

        assertThat(claims.get(TokenSubjectType.CLAIM_NAME)).isEqualTo("admin");
        assertThat(claims).doesNotContainKey("role");
    }

    private Claims 클레임(String token) {
        return Jwts.parser()
            .verifyWith(secretKey())
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    private String 토큰(String subjectType, Long subject) {
        var builder = Jwts.builder()
            .subject(String.valueOf(subject))
            .issuedAt(Date.from(NOW))
            .expiration(Date.from(NOW.plus(Duration.ofDays(1))))
            .signWith(secretKey());
        if (subjectType != null) {
            builder.claim(TokenSubjectType.CLAIM_NAME, subjectType);
        }
        return builder.compact();
    }

    private SecretKey secretKey() {
        return Keys.hmacShaKeyFor(Base64.getDecoder().decode(SECRET));
    }
}
