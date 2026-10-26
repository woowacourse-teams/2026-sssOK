package com.sssok.renewal.application.auth;

import com.sssok.renewal.application.auth.exception.SocialAuthException;
import com.sssok.renewal.application.auth.exception.UnauthorizedException;
import com.sssok.renewal.domain.auth.OAuthLoginAttempt;
import com.sssok.renewal.domain.member.Member;
import com.sssok.renewal.domain.member.Nickname;
import com.sssok.renewal.domain.member.SocialProvider;
import com.sssok.renewal.outgoing.cache.auth.OAuthLoginAttemptRepository;
import com.sssok.renewal.outgoing.database.member.MemberRepository;
import com.sssok.renewal.outgoing.oauth.AppleAuthClient;
import com.sssok.renewal.outgoing.oauth.KakaoAuthClient;
import com.sssok.support.PostgresContainerSupport;
import com.sssok.support.PostgresIntegrationTest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;


@PostgresIntegrationTest
@TestPropertySource(properties = {
    "oauth.kakao.client-id=test-client",
    "oauth.kakao.redirect-uri=https://example.com/kakao/callback",
    "oauth.apple.redirect-uri=https://example.com/apple/callback"
})
class OAuthLoginAttemptServiceTest extends PostgresContainerSupport {
    @Autowired OAuthLoginAttemptService oauthLoginAttemptService;
    @Autowired StartKakaoLoginService startKakaoLoginService;
    @Autowired MemberRepository memberRepository;
    @Autowired SessionTokenProvider sessionTokenProvider;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired OAuthLoginAttemptRepository oauthLoginAttemptRepository;
    @Autowired PlatformTransactionManager transactionManager;
    @MockitoBean KakaoAuthClient kakaoClient;
    @MockitoBean AppleAuthClient appleClient;

    @BeforeEach
    void setUp() {
        when(kakaoClient.provider()).thenReturn(SocialProvider.KAKAO);
        when(appleClient.provider()).thenReturn(SocialProvider.APPLE);
    }

    @Test
    void 카카오_시작_결과가_실제로_소비할_수_있는_익명_회원_요청과_연결된다() {
        Member member = anonymousMember();
        String token = sessionTokenProvider.issue(member.getId(), Instant.now()).value();
        SocialLoginStartResult result = startKakaoLoginService.start(List.of(token));
        OAuthLoginAttempt attempt = oauthLoginAttemptService.consume(SocialProvider.KAKAO, result.state(), result.loginVerifier());
        assertThat(attempt.anonymousMemberIds()).containsExactly(member.getId());
        assertThat(result.authorizationUrl()).contains("client_id=test-client", "state=" + result.state());
        assertThatThrownBy(() -> oauthLoginAttemptService.consume(SocialProvider.KAKAO, result.state(), result.loginVerifier()))
            .isInstanceOf(SocialAuthException.class);
    }

    @Test
    void 익명_인증에서_회원을_확인하고_정해진_콜백과_함께_저장한다() {
        Member member = anonymousMember();
        String token = sessionTokenProvider.issue(member.getId(), Instant.now()).value();

        OAuthLoginAttemptResult started = oauthLoginAttemptService.start(SocialProvider.KAKAO, List.of(token));
        OAuthLoginAttempt consumed = consume(started);

        assertThat(consumed.anonymousMemberIds()).containsExactly(member.getId());
        assertThat(consumed.redirectUri()).isEqualTo("https://example.com/kakao/callback");
        assertThat(consumed.nonce()).isNull();
        assertThat(consumed.expiresAt()).isEqualTo(consumed.createdAt().plusSeconds(300));
        assertThat(consumed.stateHash()).hasSize(64).isNotEqualTo(started.state());
        assertThat(consumed.loginVerifierHash()).hasSize(64).isNotEqualTo(started.loginVerifier());
    }

    @Test
    void 인증_없이도_시작하며_애플_nonce는_서버가_생성한_값을_반환한다() {
        OAuthLoginAttemptResult first = oauthLoginAttemptService.start(SocialProvider.APPLE, List.of());
        OAuthLoginAttemptResult second = oauthLoginAttemptService.start(SocialProvider.APPLE, List.of());
        OAuthLoginAttempt consumed = consume(first);

        assertThat(consumed.anonymousMemberIds()).isEmpty();
        assertThat(consumed.nonce()).isEqualTo(first.nonce()).hasSize(43);
        assertThat(first.state()).hasSize(43).isNotEqualTo(second.state()).isNotEqualTo(first.loginVerifier());
        assertThat(first.nonce()).isNotEqualTo(second.nonce());
        assertThat(first.toString()).doesNotContain(first.state(), first.loginVerifier(), first.nonce());
        assertThat(consumed.toString()).doesNotContain(consumed.stateHash(), consumed.nonce());
    }

    @Test
    void 잘못된_확인값이나_제공자는_거부하되_정상_요청을_소비하지_않는다() {
        OAuthLoginAttemptResult started = oauthLoginAttemptService.start(SocialProvider.KAKAO, List.of());
        OAuthLoginAttemptResult otherBrowser = oauthLoginAttemptService.start(SocialProvider.KAKAO, List.of());

        assertThatThrownBy(() -> oauthLoginAttemptService.consume(SocialProvider.KAKAO, started.state(), otherBrowser.loginVerifier()))
            .isInstanceOf(SocialAuthException.class);
        assertThatThrownBy(() -> oauthLoginAttemptService.consume(SocialProvider.APPLE, started.state(), started.loginVerifier()))
            .isInstanceOf(SocialAuthException.class);
        assertThat(consume(started).provider()).isEqualTo(SocialProvider.KAKAO);
    }

    @Test
    void 소비된_state는_다시_사용할_수_없다() {
        OAuthLoginAttemptResult started = oauthLoginAttemptService.start(SocialProvider.KAKAO, List.of());
        consume(started);
        assertThatThrownBy(() -> consume(started)).isInstanceOf(SocialAuthException.class);
    }

    @Test
    void 만료된_로그인_시도는_거부한다() {
        OAuthLoginAttemptResult started = oauthLoginAttemptService.start(SocialProvider.KAKAO, List.of());
        OAuthLoginAttempt saved = storedAttempt(started);
        oauthLoginAttemptRepository.save(new OAuthLoginAttempt(saved.stateHash(), saved.loginVerifierHash(), saved.provider(),
            saved.redirectUri(), saved.nonce(), saved.anonymousMemberIds(),
            Instant.now().minusSeconds(600), Instant.now().minusSeconds(300)));
        assertThatThrownBy(() -> consume(started)).isInstanceOf(SocialAuthException.class);
    }

    @Test
    void 저장된_리다이렉트가_현재_설정과_다르면_거부한다() {
        OAuthLoginAttemptResult started = oauthLoginAttemptService.start(SocialProvider.KAKAO, List.of());
        OAuthLoginAttempt saved = storedAttempt(started);
        oauthLoginAttemptRepository.save(new OAuthLoginAttempt(saved.stateHash(), saved.loginVerifierHash(), saved.provider(),
            "https://other.example/callback", saved.nonce(), saved.anonymousMemberIds(), saved.createdAt(), saved.expiresAt()));
        assertThatThrownBy(() -> consume(started)).isInstanceOf(SocialAuthException.class);
    }

    @Test
    void 외부_서비스_실패로_상위_트랜잭션이_롤백되어도_소비는_유지한다() {
        OAuthLoginAttemptResult started = oauthLoginAttemptService.start(SocialProvider.KAKAO, List.of());
        TransactionTemplate outerTransaction = new TransactionTemplate(transactionManager);
        outerTransaction.executeWithoutResult(status -> {
            consume(started);
            status.setRollbackOnly();
        });
        assertThatThrownBy(() -> consume(started)).isInstanceOf(SocialAuthException.class);
    }

    @Test
    void 동시에_소비해도_하나의_요청만_성공한다() throws Exception {
        OAuthLoginAttemptResult started = oauthLoginAttemptService.start(SocialProvider.KAKAO, List.of());
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Integer> first = executor.submit(() -> consumeConcurrently(started, ready, start));
            Future<Integer> second = executor.submit(() -> consumeConcurrently(started, ready, start));
            try {
                assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            } finally {
                start.countDown();
            }
            assertThat(first.get(10, TimeUnit.SECONDS) + second.get(10, TimeUnit.SECONDS)).isEqualTo(1);
        }
    }

    @Test
    void 로그인_도중_통합된_익명_회원은_활동_연결에_사용할_수_없다() {
        Member member = anonymousMember();
        String token = sessionTokenProvider.issue(member.getId(), Instant.now()).value();
        OAuthLoginAttemptResult started = oauthLoginAttemptService.start(SocialProvider.KAKAO, List.of(token));
        member.markMerged();
        memberRepository.save(member);
        assertThatThrownBy(() -> consume(started)).isInstanceOf(SocialAuthException.class);
    }

    @Test
    void 등록된_회원은_익명_활동_연결_대상으로_지정할_수_없다() {
        Member member = anonymousMember();
        member.completeRegistration();
        memberRepository.save(member);
        String token = sessionTokenProvider.issue(member.getId(), Instant.now()).value();
        assertThatThrownBy(() -> oauthLoginAttemptService.start(SocialProvider.KAKAO, List.of(token))).isInstanceOf(SocialAuthException.class);
    }

    @Test
    void 잘못된_인증을_인증_없는_시도로_취급하지_않는다() {
        assertThatThrownBy(() -> oauthLoginAttemptService.start(SocialProvider.KAKAO, List.of("invalid")))
            .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void 비활성_제공자로는_로그인을_시작할_수_없다() {
        when(kakaoClient.provider()).thenReturn(null);
        assertThatThrownBy(() -> oauthLoginAttemptService.start(SocialProvider.KAKAO, List.of())).isInstanceOf(SocialAuthException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "short", "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!"})
    void 잘못된_state_형식은_거부한다(String state) {
        OAuthLoginAttemptResult started = oauthLoginAttemptService.start(SocialProvider.KAKAO, List.of());
        assertThatThrownBy(() -> oauthLoginAttemptService.consume(SocialProvider.KAKAO, state, started.loginVerifier()))
            .isInstanceOf(SocialAuthException.class);
    }

    @Test
    void 만료_경계_시각도_만료로_취급한다() {
        OAuthLoginAttempt attempt = consume(oauthLoginAttemptService.start(SocialProvider.APPLE, List.of()));
        assertThat(attempt.isExpiredAt(attempt.expiresAt().minusNanos(1))).isFalse();
        assertThat(attempt.isExpiredAt(attempt.expiresAt())).isTrue();
    }

    @Test
    void 회원이_물리_삭제되면_남아있는_로그인_시도로_인증할_수_없다() {
        Member member = anonymousMember();
        OAuthLoginAttemptResult started = oauthLoginAttemptService.start(SocialProvider.KAKAO, List.of(sessionTokenProvider.issue(member.getId(), Instant.now()).value()));
        jdbcTemplate.update("DELETE FROM member WHERE id = ?", member.getId());
        assertThatThrownBy(() -> consume(started)).isInstanceOf(SocialAuthException.class);
    }

    @Test
    void 로그인_시도_테이블만_제거하고_서비스_인증_테이블은_유지한다() {
        assertThat(jdbcTemplate.queryForObject("SELECT to_regclass('public.oauth_login_attempt')::text", String.class)).isNull();
        assertThat(jdbcTemplate.queryForObject("SELECT to_regclass('public.auth_session')::text", String.class)).isEqualTo("auth_session");
    }

    @Test
    void 로그인_확인값이_없으면_state만으로_소비할_수_없다() {
        OAuthLoginAttemptResult started = oauthLoginAttemptService.start(SocialProvider.KAKAO, List.of());
        assertThatThrownBy(() -> oauthLoginAttemptService.consume(started.provider(), started.state(), null))
            .isInstanceOf(SocialAuthException.class);
        assertThat(consume(started)).isNotNull();
    }

    @Test
    void 로그아웃한_토큰으로는_익명_활동_연결을_시작할_수_없다() {
        Member member = anonymousMember();
        String token = sessionTokenProvider.issue(member.getId(), Instant.now()).value();
        sessionTokenProvider.logout(token);
        assertThatThrownBy(() -> oauthLoginAttemptService.start(SocialProvider.KAKAO, List.of(token)))
            .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void 여러_익명_토큰을_검증하고_중복_계정을_제거해_로그인_시도에_고정한다() {
        Member first = anonymousMember();
        Member second = anonymousMember();
        String firstToken = sessionTokenProvider.issue(first.getId(), Instant.now()).value();
        String secondToken = sessionTokenProvider.issue(second.getId(), Instant.now()).value();

        OAuthLoginAttemptResult started = oauthLoginAttemptService.start(SocialProvider.KAKAO, List.of(secondToken, firstToken, secondToken));

        assertThat(consume(started).anonymousMemberIds()).containsExactly(first.getId(), second.getId());
    }

    @Test
    void 여러_익명_토큰_중_하나라도_잘못되면_로그인을_시작하지_않는다() {
        String token = sessionTokenProvider.issue(anonymousMember().getId(), Instant.now()).value();

        assertThatThrownBy(() -> oauthLoginAttemptService.start(SocialProvider.KAKAO, List.of(token, "invalid")))
            .isInstanceOf(UnauthorizedException.class);
    }

    private OAuthLoginAttempt storedAttempt(OAuthLoginAttemptResult started) {
        try {
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(started.state().getBytes(StandardCharsets.UTF_8)));
            return oauthLoginAttemptRepository.findByStateHash(hash).orElseThrow();
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
    }

    private OAuthLoginAttempt consume(OAuthLoginAttemptResult started) {
        return oauthLoginAttemptService.consume(started.provider(), started.state(), started.loginVerifier());
    }

    private int consumeConcurrently(OAuthLoginAttemptResult started, CountDownLatch ready, CountDownLatch start)
        throws InterruptedException {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("동시 요청 시작 대기 초과");
        }
        try {
            consume(started);
            return 1;
        } catch (SocialAuthException e) {
            return 0;
        }
    }

    private Member anonymousMember() {
        return memberRepository.save(Member.register(new Nickname("익명"), Instant.now()));
    }
}
