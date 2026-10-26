package com.sssok.renewal.application.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

import com.sssok.renewal.application.auth.exception.SocialAuthException;
import com.sssok.renewal.application.auth.exception.UnauthorizedException;
import com.sssok.renewal.domain.member.Member;
import com.sssok.renewal.domain.member.MemberStatus;
import com.sssok.renewal.domain.member.Nickname;
import com.sssok.renewal.domain.member.SocialIdentity;
import com.sssok.renewal.domain.member.SocialProvider;
import com.sssok.renewal.outgoing.database.member.MemberRepository;
import com.sssok.renewal.outgoing.database.member.SocialAccountRepository;
import com.sssok.renewal.outgoing.oauth.AppleAuthClient;
import com.sssok.renewal.outgoing.oauth.KakaoAuthClient;
import com.sssok.renewal.outgoing.oauth.SocialAuthentication;
import com.sssok.support.PostgresContainerSupport;
import com.sssok.support.PostgresIntegrationTest;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@PostgresIntegrationTest
@TestPropertySource(properties = {
    "oauth.apple.client-id=test-client",
    "oauth.apple.redirect-uri=https://example.com/apple/callback",
    "oauth.kakao.redirect-uri=https://example.com/kakao/callback"
})
class CompleteAppleLoginServiceTest extends PostgresContainerSupport {
    @Autowired StartAppleLoginService startAppleLoginService;
    @Autowired StartKakaoLoginService startKakaoLoginService;
    @Autowired CompleteSocialLoginService completeSocialLoginService;
    @Autowired MemberRepository memberRepository;
    @Autowired SocialAccountRepository socialAccountRepository;
    @Autowired SessionTokenProvider sessionTokenProvider;
    @MockitoBean AppleAuthClient appleClient;
    @MockitoBean KakaoAuthClient kakaoClient;
    SocialIdentity identity;

    @BeforeEach
    void setUp() {
        identity = new SocialIdentity(SocialProvider.APPLE, UUID.randomUUID().toString());
        when(appleClient.provider()).thenReturn(SocialProvider.APPLE);
        when(kakaoClient.provider()).thenReturn(SocialProvider.KAKAO);
        when(appleClient.authenticate(anyString(), anyString())).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return new SocialAuthentication(identity, "provider-access", "provider-refresh");
        });
    }

    @Test
    void URL에_넣은_서버_nonce로_검증하고_익명_회원을_가입시킨다() {
        Member anonymous = anonymous();
        String oldToken = sessionTokenProvider.issue(anonymous.getId(), Instant.now()).value();
        SocialLoginStartResult start = startAppleLoginService.start(oldToken);
        String nonce = Arrays.stream(URI.create(start.authorizationUrl()).getRawQuery().split("&"))
            .filter(part -> part.startsWith("nonce=")).map(part -> part.substring(6))
            .map(value -> URLDecoder.decode(value, StandardCharsets.UTF_8)).findFirst().orElseThrow();
        AuthResult result = complete(start);
        verify(appleClient).authenticate("code", nonce);
        assertThat(nonce).hasSize(43);
        assertThat(result.userId()).isEqualTo(anonymous.getId());
        assertThat(result.nickname()).isEqualTo("익명사용자");
        assertThat(sessionTokenProvider.parse(result.accessToken())).isEqualTo(anonymous.getId());
        assertThat(memberRepository.findById(anonymous.getId()).orElseThrow().getStatus()).isEqualTo(MemberStatus.REGISTERED);
        assertThatThrownBy(() -> sessionTokenProvider.parse(oldToken)).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void 인증없이_신규가입_후_동일한_애플_회원으로_로그인한다() {
        AuthResult first = complete(startAppleLoginService.start(null));
        AuthResult second = complete(startAppleLoginService.start(null));
        assertThat(second.userId()).isEqualTo(first.userId());
        assertThat(second.nickname()).isEqualTo("사용자");
        assertThat(sessionTokenProvider.parse(first.accessToken())).isEqualTo(first.userId());
        assertThat(sessionTokenProvider.parse(second.accessToken())).isEqualTo(first.userId());
    }

    @Test
    void 기존_애플_회원에_익명_활동을_통합한다() {
        AuthResult registered = complete(startAppleLoginService.start(null));
        Member anonymous = anonymous();
        AuthResult merged = complete(startAppleLoginService.start(sessionTokenProvider.issue(anonymous.getId(), Instant.now()).value()));
        assertThat(merged.userId()).isEqualTo(registered.userId());
        assertThat(memberRepository.findById(anonymous.getId()).orElseThrow().getStatus()).isEqualTo(MemberStatus.MERGED);
        assertThat(sessionTokenProvider.parse(registered.accessToken())).isEqualTo(registered.userId());
    }

    @Test
    void 다른_제공자의_시도는_소비하지_않는다() {
        SocialLoginStartResult start = startAppleLoginService.start(null);
        assertThatThrownBy(() -> completeSocialLoginService.login(SocialProvider.KAKAO, "code", start.state(), start.loginVerifier()))
            .isInstanceOf(SocialAuthException.class);
        verify(kakaoClient, never()).authenticate(anyString(), isNull());
        verify(appleClient, never()).authenticate(anyString(), anyString());
        complete(start);
    }

    @Test
    void 같은_subject라도_카카오와_애플은_다른_회원이다() {
        SocialIdentity kakaoIdentity = new SocialIdentity(SocialProvider.KAKAO, identity.providerUserId());
        when(kakaoClient.authenticate(anyString(), isNull()))
            .thenReturn(new SocialAuthentication(kakaoIdentity, "access", "refresh"));
        SocialLoginStartResult kakaoStart = startKakaoLoginService.start(null);
        AuthResult kakao = completeSocialLoginService.login(SocialProvider.KAKAO, "code", kakaoStart.state(), kakaoStart.loginVerifier());
        AuthResult apple = complete(startAppleLoginService.start(null));
        assertThat(apple.userId()).isNotEqualTo(kakao.userId());
    }

    @Test
    void 검증_실패시_가입하지_않고_소비된_시도를_복원하지_않는다() {
        SocialLoginStartResult start = startAppleLoginService.start(null);
        when(appleClient.authenticate(anyString(), anyString())).thenThrow(SocialAuthException.invalid());
        assertThatThrownBy(() -> complete(start)).isInstanceOf(SocialAuthException.class);
        assertThat(socialAccountRepository.findByIdentity(identity)).isEmpty();
        assertThatThrownBy(() -> complete(start)).isInstanceOf(SocialAuthException.class);
        verify(appleClient, times(1)).authenticate(anyString(), anyString());
    }

    @Test
    void 잘못된_확인값은_제공자_호출_전에_거부하고_정상_시도를_보존한다() {
        SocialLoginStartResult start = startAppleLoginService.start(null);
        assertThatThrownBy(() -> completeSocialLoginService.login(SocialProvider.APPLE, "code", start.state(), "x".repeat(43)))
            .isInstanceOf(SocialAuthException.class);
        verify(appleClient, never()).authenticate(anyString(), anyString());
        complete(start);
        assertThatThrownBy(() -> complete(start)).isInstanceOf(SocialAuthException.class);
        verify(appleClient, times(1)).authenticate(anyString(), anyString());
    }

    private Member anonymous() {
        return memberRepository.save(Member.register(new Nickname("익명사용자"), Instant.now()));
    }

    private AuthResult complete(SocialLoginStartResult start) {
        return completeSocialLoginService.login(SocialProvider.APPLE, "code", start.state(), start.loginVerifier());
    }
}
