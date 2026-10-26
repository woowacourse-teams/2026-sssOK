package com.sssok.renewal.application.auth;

import com.sssok.renewal.application.auth.exception.UnauthorizedException;
import com.sssok.renewal.domain.auth.RefreshTokenDelivery;
import com.sssok.renewal.domain.member.Member;
import com.sssok.renewal.domain.member.Nickname;
import com.sssok.renewal.outgoing.database.member.MemberRepository;
import com.sssok.renewal.outgoing.security.JwtTokenProvider;
import com.sssok.renewal.outgoing.security.TokenProvider.IssuedToken;
import com.sssok.support.PostgresContainerSupport;
import com.sssok.support.PostgresIntegrationTest;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


@TestPropertySource(properties = "jwt.access-token-ttl=30m")
@PostgresIntegrationTest
class RefreshTokenTest extends PostgresContainerSupport {
    @Autowired SessionTokenProvider tokens;
    @Autowired AnonymousAuthService anonymous;
    @Autowired MemberRepository members;
    @Autowired JwtTokenProvider jwt;
    @Autowired JdbcTemplate jdbc;

    @Test
    void 익명도_발급하고_원문은_저장하지_않으며_회전해도_30일_만료는_유지한다() {
        AuthResult first = anonymous.authenticate("갱신");
        assertThat(first.refreshToken()).hasSize(43);
        assertThat(Duration.between(first.expiresAt(), first.refreshExpiresAt()))
            .isEqualTo(Duration.ofDays(30).minusMinutes(30));
        String sessionId = jwt.verify(first.accessToken()).sessionId();
        assertThat(jdbc.queryForObject("select token_hash from refresh_token where session_id = ?",
            String.class, sessionId)).hasSize(64).isNotEqualTo(first.refreshToken());

        AuthResult next = tokens.refresh(first.refreshToken(), RefreshTokenDelivery.COOKIE);
        assertThat(next.userId()).isEqualTo(first.userId());
        assertThat(next.refreshToken()).isNotEqualTo(first.refreshToken());
        assertThat(next.refreshExpiresAt()).isCloseTo(first.refreshExpiresAt(),
            Assertions.within(1, ChronoUnit.MILLIS));
        assertThat(jwt.verify(next.accessToken()).sessionId()).isEqualTo(sessionId);
    }

    @Test
    void 이전_토큰_재사용은_새_토큰까지_폐기하되_다른_기기에는_영향이_없다() {
        AuthResult first = anonymous.authenticate("재사용");
        IssuedToken other = tokens.issue(first.userId(), Instant.now());
        AuthResult next = tokens.refresh(first.refreshToken(), RefreshTokenDelivery.COOKIE);
        rejected(first.refreshToken(), RefreshTokenDelivery.COOKIE);
        rejected(next.refreshToken(), RefreshTokenDelivery.COOKIE);
        assertThatThrownBy(() -> tokens.parse(next.accessToken())).isInstanceOf(UnauthorizedException.class);
        assertThat(tokens.parse(other.value())).isEqualTo(first.userId());
        assertThat(tokens.refresh(other.refreshToken(), RefreshTokenDelivery.COOKIE).userId()).isEqualTo(first.userId());
    }

    @Test
    void 전달방식_불일치는_거절하고_정상_방식으로는_갱신할_수_있다() {
        AuthResult first = anonymous.authenticate("앱", RefreshTokenDelivery.BODY);
        rejected(first.refreshToken(), RefreshTokenDelivery.COOKIE);
        assertThat(tokens.refresh(first.refreshToken(), RefreshTokenDelivery.BODY).userId()).isEqualTo(first.userId());
    }

    @Test
    void access가_만료되어도_refresh는_사용할_수_있다() {
        Member member = members.save(Member.register(new Nickname("만료"), Instant.now()));
        IssuedToken first = tokens.issue(member.getId(), Instant.now().minus(Duration.ofHours(1)));
        assertThatThrownBy(() -> tokens.parse(first.value())).isInstanceOf(UnauthorizedException.class);
        assertThat(tokens.refresh(first.refreshToken(), RefreshTokenDelivery.COOKIE).userId()).isEqualTo(member.getId());
    }

    @Test
    void 세션_만료_직전_access도_세션_만료를_넘지_않는다() {
        Member member = members.save(Member.register(new Nickname("마지막"), Instant.now()));
        IssuedToken first = tokens.issue(member.getId(), Instant.now().minus(Duration.ofDays(30)).plusSeconds(60));
        AuthResult next = tokens.refresh(first.refreshToken(), RefreshTokenDelivery.COOKIE);
        assertThat(next.expiresAt()).isEqualTo(next.refreshExpiresAt());
        jdbc.update("update auth_session set expires_at = ? where id = ?",
            Timestamp.from(Instant.now().minusSeconds(1)), jwt.verify(next.accessToken()).sessionId());
        rejected(next.refreshToken(), RefreshTokenDelivery.COOKIE);
    }

    @Test
    void 로그아웃과_전체폐기_및_탈퇴상태는_갱신을_차단한다() {
        AuthResult first = anonymous.authenticate("로그아웃");
        tokens.logout(first.accessToken());
        rejected(first.refreshToken(), RefreshTokenDelivery.COOKIE);
        AuthResult second = anonymous.authenticate("폐기");
        tokens.revokeAll(second.userId());
        rejected(second.refreshToken(), RefreshTokenDelivery.COOKIE);
        AuthResult third = anonymous.authenticate("탈퇴");
        Member member = members.findById(third.userId()).orElseThrow();
        member.completeRegistration();
        member.withdraw();
        members.save(member);
        rejected(third.refreshToken(), RefreshTokenDelivery.COOKIE);
    }

    @Test
    void 동일_토큰의_동시갱신은_한번만_성공하고_재사용된_세션은_폐기한다() throws Exception {
        AuthResult first = anonymous.authenticate("동시");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<Boolean> refresh = () -> {
            ready.countDown();
            start.await();
            try {
                tokens.refresh(first.refreshToken(), RefreshTokenDelivery.COOKIE);
                return true;
            } catch (UnauthorizedException e) {
                return false;
            }
        };
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Boolean> one = executor.submit(refresh);
            Future<Boolean> two = executor.submit(refresh);
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(List.of(one.get(10, TimeUnit.SECONDS),
                two.get(10, TimeUnit.SECONDS))).containsExactlyInAnyOrder(true, false);
        }
        assertThatThrownBy(() -> tokens.parse(first.accessToken())).isInstanceOf(UnauthorizedException.class);
    }

    private void rejected(String token, RefreshTokenDelivery delivery) {
        assertThatThrownBy(() -> tokens.refresh(token, delivery)).isInstanceOf(UnauthorizedException.class);
    }
}
