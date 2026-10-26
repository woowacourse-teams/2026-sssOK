package com.sssok.renewal.application.auth;

import com.sssok.common.exception.ErrorCode;
import com.sssok.renewal.application.auth.exception.LinkCodeNotFoundException;
import com.sssok.renewal.application.auth.exception.SocialAuthException;
import com.sssok.renewal.application.auth.exception.UnauthorizedException;
import com.sssok.renewal.domain.member.*;
import com.sssok.renewal.outgoing.database.member.MemberRepository;
import com.sssok.renewal.outgoing.database.member.SocialAccountRepository;
import com.sssok.renewal.outgoing.oauth.KakaoAuthClient;
import com.sssok.renewal.outgoing.oauth.SocialAuthentication;
import com.sssok.support.PostgresContainerSupport;
import com.sssok.support.PostgresIntegrationTest;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;


@PostgresIntegrationTest
@TestPropertySource(properties = {
    "oauth.kakao.client-id=test-client",
    "oauth.kakao.redirect-uri=https://example.com/kakao/callback"
})
class CompleteKakaoLoginServiceTest extends PostgresContainerSupport {
    @Autowired StartKakaoLoginService startKakaoLoginService;
    @Autowired CompleteSocialLoginService completeSocialLoginService;
    @Autowired MemberRepository memberRepository;
    @Autowired JdbcTemplate jdbc;
    @Autowired SocialAccountRepository socialAccountRepository;
    @Autowired SessionTokenProvider sessionTokenProvider;
    @Autowired IssueLinkCodeService issueLinkCodeService;
    @Autowired LinkLoginService linkLoginService;
    @Autowired PlatformTransactionManager transactionManager;
    @MockitoBean KakaoAuthClient kakaoClient;
    SocialIdentity identity;

    @BeforeEach
    void setUp() {
        identity = new SocialIdentity(SocialProvider.KAKAO, UUID.randomUUID().toString());
        when(kakaoClient.provider()).thenReturn(SocialProvider.KAKAO);
        when(kakaoClient.authenticate(anyString(), isNull())).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return new SocialAuthentication(identity, "provider-access", "provider-refresh");
        });
    }

    @Test
    void 인증_없이_가입한_회원은_같은_소셜_계정으로_다시_로그인한다() {
        AuthResult first = complete(startKakaoLoginService.start(List.of()));
        AuthResult second = complete(startKakaoLoginService.start(List.of()));
        assertThat(second.userId()).isEqualTo(first.userId());
        assertThat(second.nickname()).isEqualTo("사용자");
        assertThat(second.accessToken()).isNotEqualTo(first.accessToken());
        assertThat(sessionTokenProvider.parse(first.accessToken())).isEqualTo(first.userId());
        assertThat(sessionTokenProvider.parse(second.accessToken())).isEqualTo(first.userId());
        assertThat(memberRepository.findById(first.userId()).orElseThrow().getStatus()).isEqualTo(MemberStatus.REGISTERED);
        assertThat(socialAccountRepository.findByIdentity(identity).orElseThrow().memberId()).isEqualTo(first.userId());
    }

    @Test
    void 익명_가입은_회원과_닉네임을_유지하고_기존_인증과_연결코드를_폐기한다() {
        Member anonymous = anonymous();
        String oldToken = sessionTokenProvider.issue(anonymous.getId(), Instant.now()).value();
        LinkCodeResult link = issueLinkCodeService.issue(anonymous.getId());
        AuthResult result = complete(startKakaoLoginService.start(List.of(oldToken)));
        assertThat(result.userId()).isEqualTo(anonymous.getId());
        assertThat(result.nickname()).isEqualTo("기존닉네임");
        assertThat(sessionTokenProvider.parse(result.accessToken())).isEqualTo(anonymous.getId());
        assertThatThrownBy(() -> sessionTokenProvider.parse(oldToken)).isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(() -> linkLoginService.login(link.linkCode())).isInstanceOf(LinkCodeNotFoundException.class);
    }

    @Test
    void 잘못된_확인값은_제공자를_호출하지_않고_정상_시도는_보존한다() {
        SocialLoginStartResult start = startKakaoLoginService.start(List.of());
        assertThatThrownBy(() -> completeSocialLoginService.login(SocialProvider.KAKAO, "code", start.state(), "x".repeat(43)))
            .isInstanceOf(SocialAuthException.class);
        verify(kakaoClient, never()).authenticate(anyString(), isNull());
        complete(start);
    }

    @Test
    void 사용한_시도는_다시_인증할_수_없다() {
        SocialLoginStartResult start = startKakaoLoginService.start(List.of());
        complete(start);
        assertThatThrownBy(() -> complete(start)).isInstanceOf(SocialAuthException.class);
        verify(kakaoClient, times(1)).authenticate(anyString(), isNull());
    }

    @Test
    void 제공자_실패시_가입하지_않고_시도를_복원하지_않는다() {
        SocialLoginStartResult start = startKakaoLoginService.start(List.of());
        when(kakaoClient.authenticate(anyString(), isNull())).thenThrow(SocialAuthException.unavailable());
        assertThatThrownBy(() -> complete(start)).isInstanceOf(SocialAuthException.class);
        assertThat(socialAccountRepository.findByIdentity(identity)).isEmpty();
        assertThatThrownBy(() -> complete(start)).isInstanceOf(SocialAuthException.class);
        verify(kakaoClient, times(1)).authenticate(anyString(), isNull());
    }

    @Test
    void 제공자_응답을_기다리는_사이_변경된_익명_상태를_다시_확인한다() {
        Member anonymous = anonymous();
        SocialLoginStartResult start = startKakaoLoginService.start(List.of(sessionTokenProvider.issue(anonymous.getId(), Instant.now()).value()));
        when(kakaoClient.authenticate(anyString(), isNull())).thenAnswer(invocation -> {
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                anonymous.markMerged();
                memberRepository.save(anonymous);
            });
            return new SocialAuthentication(identity, "access", "refresh");
        });
        assertThatThrownBy(() -> complete(start)).isInstanceOf(SocialAuthException.class);
        assertThat(socialAccountRepository.findByIdentity(identity)).isEmpty();
    }

    @Test
    void 기존_계정으로_로그인하면_익명_회원을_통합하고_인증을_교체한다() {
        AuthResult registered = complete(startKakaoLoginService.start(List.of()));
        Member anonymous = anonymous();
        String oldToken = sessionTokenProvider.issue(anonymous.getId(), Instant.now()).value();
        SocialLoginStartResult start = startKakaoLoginService.start(List.of(oldToken));
        AuthResult merged = complete(start);
        assertThat(merged.userId()).isEqualTo(registered.userId());
        assertThat(sessionTokenProvider.parse(merged.accessToken())).isEqualTo(registered.userId());
        assertThat(sessionTokenProvider.parse(registered.accessToken())).isEqualTo(registered.userId());
        assertThatThrownBy(() -> sessionTokenProvider.parse(oldToken)).isInstanceOf(UnauthorizedException.class);
        assertThat(memberRepository.findById(anonymous.getId()).orElseThrow().getStatus()).isEqualTo(MemberStatus.MERGED);
        assertThat(socialAccountRepository.findByIdentity(identity).orElseThrow().memberId()).isEqualTo(registered.userId());
    }

    @Test
    void 탈퇴한_계정으로_토큰을_발급하지_않는다() {
        AuthResult registered = complete(startKakaoLoginService.start(List.of()));
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Member member = memberRepository.findById(registered.userId()).orElseThrow();
            member.withdraw();
            memberRepository.save(member);
        });
        SocialLoginStartResult start = startKakaoLoginService.start(List.of());
        assertThatThrownBy(() -> complete(start)).isInstanceOf(SocialAuthException.class);
    }

    @Test
    void 같은_계정의_동시_가입은_중복회원이나_고아회원을_남기지_않는다() throws Exception {
        SocialLoginStartResult first = startKakaoLoginService.start(List.of());
        SocialLoginStartResult second = startKakaoLoginService.start(List.of());
        long before = jdbc.queryForObject("select count(*) from member", Long.class);
        CyclicBarrier barrier = new CyclicBarrier(2);
        when(kakaoClient.authenticate(anyString(), isNull())).thenAnswer(invocation -> {
            barrier.await(10, TimeUnit.SECONDS);
            return new SocialAuthentication(identity, "access", "refresh");
        });
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<AuthResult> a = executor.submit(() -> completeOrConflict(first));
            Future<AuthResult> b = executor.submit(() -> completeOrConflict(second));
            List<AuthResult> outcomes = Stream.of(
                a.get(15, TimeUnit.SECONDS),
                b.get(15, TimeUnit.SECONDS)).filter(Objects::nonNull).toList();
            assertThat(outcomes).isNotEmpty();
            Long memberId = socialAccountRepository.findByIdentity(identity).orElseThrow().memberId();
            assertThat(outcomes).allSatisfy(result -> assertThat(result.userId()).isEqualTo(memberId));
            assertThat(jdbc.queryForObject("select count(*) from member", Long.class)).isEqualTo(before + 1);
        }
    }

    private AuthResult completeOrConflict(SocialLoginStartResult start) {
        try {
            return complete(start);
        } catch (SocialAuthException e) {
            assertThat(e.errorCode()).isEqualTo(ErrorCode.SOCIAL_AUTH_CONFLICT);
            return null;
        }
    }

    @Test
    void 빈_인가코드는_로그인_시도를_소비하지_않는다() {
        SocialLoginStartResult start = startKakaoLoginService.start(List.of());
        assertThatThrownBy(() -> completeSocialLoginService.login(SocialProvider.KAKAO, " ", start.state(), start.loginVerifier()))
            .isInstanceOf(SocialAuthException.class);
        verify(kakaoClient, never()).authenticate(anyString(), isNull());
        complete(start);
    }

    @Test
    void 카카오가_아닌_식별자는_가입에_사용하지_않는다() {
        SocialLoginStartResult start = startKakaoLoginService.start(List.of());
        SocialIdentity apple = new SocialIdentity(SocialProvider.APPLE, UUID.randomUUID().toString());
        when(kakaoClient.authenticate(anyString(), isNull()))
            .thenReturn(new SocialAuthentication(apple, "access", "refresh"));
        assertThatThrownBy(() -> complete(start)).isInstanceOf(SocialAuthException.class);
        assertThat(socialAccountRepository.findByIdentity(apple)).isEmpty();
    }

    private Member anonymous() {
        return memberRepository.save(Member.register(new Nickname("기존닉네임"), Instant.now()));
    }

    private AuthResult complete(SocialLoginStartResult start) {
        return completeSocialLoginService.login(SocialProvider.KAKAO, "code", start.state(), start.loginVerifier());
    }
}
