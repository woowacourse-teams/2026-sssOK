package com.sssok.renewal.application.auth;

import com.sssok.common.exception.ErrorCode;
import com.sssok.renewal.application.auth.exception.SocialAuthException;
import com.sssok.renewal.domain.auth.EncryptedSocialTokens;
import com.sssok.renewal.domain.auth.OAuthClientType;
import com.sssok.renewal.domain.auth.RefreshTokenDelivery;
import com.sssok.renewal.domain.member.SocialIdentity;
import com.sssok.renewal.domain.member.SocialProvider;
import com.sssok.renewal.outgoing.database.member.SocialAccountRepository;
import com.sssok.renewal.outgoing.oauth.AppleAuthClient;
import com.sssok.renewal.outgoing.oauth.KakaoAuthClient;
import com.sssok.renewal.outgoing.oauth.SocialAuthentication;
import com.sssok.support.PostgresContainerSupport;
import com.sssok.support.PostgresIntegrationTest;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

@PostgresIntegrationTest
@TestPropertySource(properties = {
    "oauth.apple.client-id=web-client",
    "oauth.apple.native-client-id=com.example.app",
    "oauth.apple.redirect-uri=https://example.com/apple/callback",
    "oauth.kakao.redirect-uris=https://example.com/kakao/callback"
})
class NativeSocialLoginServiceTest extends PostgresContainerSupport {
    @Autowired NativeSocialLoginService nativeLogin;
    @Autowired StartAppleLoginService startWeb;
    @Autowired StartKakaoLoginService startKakaoWeb;
    @Autowired CompleteSocialLoginService completeWeb;
    @Autowired SocialAccountRepository socialAccountRepository;
    @Autowired SessionTokenProvider sessionTokenProvider;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean AppleAuthClient appleClient;
    @MockitoBean KakaoAuthClient kakaoClient;
    SocialIdentity identity;
    SocialIdentity kakaoIdentity;

    @BeforeEach
    void setUp() {
        identity = new SocialIdentity(SocialProvider.APPLE, UUID.randomUUID().toString());
        when(appleClient.provider()).thenReturn(SocialProvider.APPLE);
        when(appleClient.nativeConfigured()).thenReturn(true);
        kakaoIdentity = new SocialIdentity(SocialProvider.KAKAO, String.valueOf(Math.abs(UUID.randomUUID().getMostSignificantBits())));
        when(kakaoClient.provider()).thenReturn(SocialProvider.KAKAO);
        when(kakaoClient.authenticateAccessToken(anyString())).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return new SocialAuthentication(kakaoIdentity, invocation.getArgument(0), null);
        });
        when(appleClient.authenticateNative(anyString(), anyString())).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return new SocialAuthentication(identity, "native-access", "native-refresh", OAuthClientType.NATIVE);
        });
        when(appleClient.authenticate(anyString(), anyString(), anyString())).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return new SocialAuthentication(identity, "web-access", "web-refresh", OAuthClientType.WEB);
        });
    }

    @Test
    void 카카오_앱_토큰으로_신규_가입하고_다시_로그인하면_같은_회원이다() {
        AuthResult first = nativeLogin.loginWithKakao("sdk-token", RefreshTokenDelivery.COOKIE);
        AuthResult second = nativeLogin.loginWithKakao("sdk-token", RefreshTokenDelivery.COOKIE);
        assertThat(second.userId()).isEqualTo(first.userId());
        assertThat(first.nickname()).isEqualTo("사용자");
        assertThat(sessionTokenProvider.parse(second.accessToken())).isEqualTo(first.userId());
        verify(kakaoClient, times(2)).authenticateAccessToken("sdk-token");
    }

    @Test
    void 같은_카카오_계정의_웹과_앱_로그인은_같은_회원을_사용한다() {
        when(kakaoClient.authenticate(anyString(), isNull(), anyString()))
            .thenReturn(new SocialAuthentication(kakaoIdentity, "web-access", "web-refresh"));
        SocialLoginStartResult web = startKakaoWeb.start(List.of(), "https://example.com");
        AuthResult webResult = completeWeb.login(SocialProvider.KAKAO, "code", web.state(), web.loginVerifier());
        AuthResult nativeResult = nativeLogin.loginWithKakao("sdk-token", RefreshTokenDelivery.COOKIE);
        assertThat(nativeResult.userId()).isEqualTo(webResult.userId());
    }

    @Test
    void 카카오_토큰_검증이_실패하면_회원과_계정을_만들지_않는다() {
        when(kakaoClient.authenticateAccessToken(anyString())).thenThrow(SocialAuthException.invalid());
        assertThatThrownBy(() -> nativeLogin.loginWithKakao("bad-token", RefreshTokenDelivery.COOKIE))
            .isInstanceOfSatisfying(SocialAuthException.class, e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.INVALID_SOCIAL_AUTH));
        assertThat(socialAccountRepository.findByIdentity(kakaoIdentity)).isEmpty();
    }

    @Test
    void 같은_신규_카카오_계정의_동시_가입은_한_회원만_만들고_나머지는_충돌이거나_로그인이다() throws Exception {
        int threads = 6;
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<Long>> futures = java.util.stream.IntStream.range(0, threads).mapToObj(i -> pool.submit(() -> {
                ready.countDown();
                go.await();
                try {
                    return nativeLogin.loginWithKakao("sdk-token", RefreshTokenDelivery.COOKIE).userId();
                } catch (SocialAuthException e) {
                    assertThat(e.errorCode()).isEqualTo(ErrorCode.SOCIAL_AUTH_CONFLICT);
                    return -1L;
                }
            })).toList();
            ready.await(5, TimeUnit.SECONDS);
            go.countDown();
            List<Long> userIds = new java.util.ArrayList<>();
            for (Future<Long> future : futures) {
                userIds.add(future.get(15, TimeUnit.SECONDS));
            }
            assertThat(userIds.stream().filter(id -> id > 0).distinct()).hasSize(1);
            assertThat(socialAccountRepository.findByIdentity(kakaoIdentity)).isPresent();
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void 준비가_발급한_nonce로_검증하고_웹_URL없이_가입시킨다() {
        OAuthLoginAttemptResult start = nativeLogin.startApple();
        assertThat(start.state()).hasSize(43);
        assertThat(start.loginVerifier()).hasSize(43).isNotEqualTo(start.state());
        assertThat(start.nonce()).hasSize(43);
        assertThat(start.expiresAt()).isAfter(java.time.Instant.now());

        AuthResult result = nativeLogin.loginWithApple("sdk-code", start.state(), start.loginVerifier(), RefreshTokenDelivery.COOKIE);

        verify(appleClient).authenticateNative("sdk-code", start.nonce());
        verify(appleClient, never()).authenticate(anyString(), anyString(), anyString());
        assertThat(result.nickname()).isEqualTo("사용자");
        assertThat(sessionTokenProvider.parse(result.accessToken())).isEqualTo(result.userId());
        assertThat(tokenClientType()).isEqualTo("NATIVE");
    }

    @Test
    void 요청_본문의_nonce나_사용자_ID는_받지_않는다() {
        // 완료 입력은 code/state/loginVerifier뿐이며, nonce는 저장된 시도에서만 읽는다.
        OAuthLoginAttemptResult first = nativeLogin.startApple();
        OAuthLoginAttemptResult second = nativeLogin.startApple();
        nativeLogin.loginWithApple("sdk-code", second.state(), second.loginVerifier(), RefreshTokenDelivery.COOKIE);
        verify(appleClient).authenticateNative("sdk-code", second.nonce());
        verify(appleClient, never()).authenticateNative("sdk-code", first.nonce());
    }

    @Test
    void 같은_애플_계정의_웹과_앱_로그인은_같은_회원을_사용한다() {
        SocialLoginStartResult web = startWeb.start(List.of());
        AuthResult webResult = completeWeb.login(SocialProvider.APPLE, "web-code", web.state(), web.loginVerifier());
        OAuthLoginAttemptResult nativeStart = nativeLogin.startApple();
        AuthResult nativeResult = nativeLogin.loginWithApple("sdk-code", nativeStart.state(), nativeStart.loginVerifier(),
            RefreshTokenDelivery.COOKIE);
        assertThat(nativeResult.userId()).isEqualTo(webResult.userId());
    }

    @Test
    void 앱_시도는_웹_완료_API에서_거절되고_정상_시도는_보존된다() {
        OAuthLoginAttemptResult start = nativeLogin.startApple();
        assertThatThrownBy(() -> completeWeb.login(SocialProvider.APPLE, "code", start.state(), start.loginVerifier()))
            .isInstanceOfSatisfying(SocialAuthException.class, e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.INVALID_SOCIAL_AUTH));
        verify(appleClient, never()).authenticate(anyString(), anyString(), anyString());
        verify(appleClient, never()).authenticateNative(anyString(), anyString());
        nativeLogin.loginWithApple("sdk-code", start.state(), start.loginVerifier(), RefreshTokenDelivery.COOKIE);
    }

    @Test
    void 웹_시도는_앱_완료_API에서_거절되고_정상_시도는_보존된다() {
        SocialLoginStartResult start = startWeb.start(List.of());
        assertThatThrownBy(() -> nativeLogin.loginWithApple("code", start.state(), start.loginVerifier(), RefreshTokenDelivery.COOKIE))
            .isInstanceOfSatisfying(SocialAuthException.class, e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.INVALID_SOCIAL_AUTH));
        verify(appleClient, never()).authenticateNative(anyString(), anyString());
        completeWeb.login(SocialProvider.APPLE, "code", start.state(), start.loginVerifier());
    }

    @Test
    void 잘못된_확인값은_교환_전에_거부하고_시도를_보존한다() {
        OAuthLoginAttemptResult start = nativeLogin.startApple();
        assertThatThrownBy(() -> nativeLogin.loginWithApple("code", start.state(), "x".repeat(43), RefreshTokenDelivery.COOKIE))
            .isInstanceOf(SocialAuthException.class);
        assertThatThrownBy(() -> nativeLogin.loginWithApple("code", "not-a-state", start.loginVerifier(), RefreshTokenDelivery.COOKIE))
            .isInstanceOf(SocialAuthException.class);
        verify(appleClient, never()).authenticateNative(anyString(), anyString());
        nativeLogin.loginWithApple("code", start.state(), start.loginVerifier(), RefreshTokenDelivery.COOKIE);
    }

    @Test
    void 빈_코드는_시도를_소비하기_전에_거부한다() {
        OAuthLoginAttemptResult start = nativeLogin.startApple();
        assertThatThrownBy(() -> nativeLogin.loginWithApple(" ", start.state(), start.loginVerifier(), RefreshTokenDelivery.COOKIE))
            .isInstanceOf(SocialAuthException.class);
        nativeLogin.loginWithApple("code", start.state(), start.loginVerifier(), RefreshTokenDelivery.COOKIE);
    }

    @Test
    void 재사용한_시도는_거절한다() {
        OAuthLoginAttemptResult start = nativeLogin.startApple();
        nativeLogin.loginWithApple("code", start.state(), start.loginVerifier(), RefreshTokenDelivery.COOKIE);
        assertThatThrownBy(() -> nativeLogin.loginWithApple("code", start.state(), start.loginVerifier(), RefreshTokenDelivery.COOKIE))
            .isInstanceOf(SocialAuthException.class);
        verify(appleClient, times(1)).authenticateNative(anyString(), anyString());
    }

    @Test
    void 교환_실패시_가입하지_않고_소비된_시도를_복원하거나_재시도하지_않는다() {
        OAuthLoginAttemptResult start = nativeLogin.startApple();
        when(appleClient.authenticateNative(anyString(), anyString())).thenThrow(SocialAuthException.unavailable());
        assertThatThrownBy(() -> nativeLogin.loginWithApple("code", start.state(), start.loginVerifier(), RefreshTokenDelivery.COOKIE))
            .isInstanceOfSatisfying(SocialAuthException.class, e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.SOCIAL_AUTH_UNAVAILABLE));
        assertThat(socialAccountRepository.findByIdentity(identity)).isEmpty();
        assertThatThrownBy(() -> nativeLogin.loginWithApple("code", start.state(), start.loginVerifier(), RefreshTokenDelivery.COOKIE))
            .isInstanceOfSatisfying(SocialAuthException.class, e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.INVALID_SOCIAL_AUTH));
        verify(appleClient, times(1)).authenticateNative(anyString(), anyString());
    }

    @Test
    void 같은_시도의_동시_완료는_교환과_로그인에_한_번만_진입한다() throws Exception {
        OAuthLoginAttemptResult start = nativeLogin.startApple();
        when(appleClient.authenticateNative(anyString(), anyString())).thenAnswer(invocation -> {
            Thread.sleep(200);
            return new SocialAuthentication(identity, "native-access", "native-refresh", OAuthClientType.NATIVE);
        });
        int threads = 8;
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<Boolean>> futures = java.util.stream.IntStream.range(0, threads).mapToObj(i -> pool.submit(() -> {
                ready.countDown();
                go.await();
                try {
                    nativeLogin.loginWithApple("code", start.state(), start.loginVerifier(), RefreshTokenDelivery.COOKIE);
                    return true;
                } catch (SocialAuthException e) {
                    return false;
                }
            })).toList();
            ready.await(5, TimeUnit.SECONDS);
            go.countDown();
            int successes = 0;
            for (Future<Boolean> future : futures) {
                if (future.get(10, TimeUnit.SECONDS)) {
                    successes++;
                }
            }
            assertThat(successes).isEqualTo(1);
            verify(appleClient, times(1)).authenticateNative(anyString(), anyString());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void 앱_client가_설정되지_않으면_준비와_완료_모두_사용할_수_없다() {
        OAuthLoginAttemptResult start = nativeLogin.startApple();
        when(appleClient.nativeConfigured()).thenReturn(false);
        assertThatThrownBy(nativeLogin::startApple).isInstanceOfSatisfying(SocialAuthException.class,
            e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.SOCIAL_AUTH_UNAVAILABLE));
        assertThatThrownBy(() -> nativeLogin.loginWithApple("code", start.state(), start.loginVerifier(), RefreshTokenDelivery.COOKIE))
            .isInstanceOfSatisfying(SocialAuthException.class, e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.SOCIAL_AUTH_UNAVAILABLE));
        verify(appleClient, never()).authenticateNative(anyString(), anyString());
    }

    @Test
    void 웹_앱_재로그인마다_토큰과_발급_client가_같은_발급_건을_가리킨다() {
        // 웹 로그인: refresh 있음
        loginWeb("web-access", "web-refresh");
        assertStored(OAuthClientType.WEB, true);
        // 앱 로그인(refresh 없음): 웹 refresh를 앱 메타데이터에 붙여 두면 안 된다.
        loginNative("native-access", null);
        assertStored(OAuthClientType.NATIVE, false);
        // 앱 로그인(refresh 있음)
        loginNative("native-access2", "native-refresh2");
        assertStored(OAuthClientType.NATIVE, true);
        // 같은 client로 refresh 없이 다시 로그인하면 기존 값을 유지한다.
        loginNative("native-access3", null);
        assertStored(OAuthClientType.NATIVE, true);
        // 웹으로 돌아오면 앱 refresh를 웹 메타데이터에 붙이지 않는다.
        loginWeb("web-access3", null);
        assertStored(OAuthClientType.WEB, false);
    }

    @Test
    void 메타데이터가_없는_기존_토큰은_웹_설정으로_읽는다() {
        loginWeb("web-access", "web-refresh");
        jdbc.update("UPDATE social_account SET token_client_type = NULL WHERE provider_user_id = ?", identity.providerUserId());
        EncryptedSocialTokens tokens = socialAccountRepository.findTokens(identity).orElseThrow();
        assertThat(tokens.clientType()).isEqualTo(OAuthClientType.WEB);
        // 메타데이터가 없던 웹 토큰은 refresh 없는 웹 재로그인에서도 유지된다.
        loginWeb("web-access2", null);
        assertStored(OAuthClientType.WEB, true);
    }

    private void loginWeb(String access, String refresh) {
        when(appleClient.authenticate(anyString(), anyString(), anyString()))
            .thenReturn(new SocialAuthentication(identity, access, refresh, OAuthClientType.WEB));
        SocialLoginStartResult start = startWeb.start(List.of());
        completeWeb.login(SocialProvider.APPLE, "code", start.state(), start.loginVerifier());
    }

    private void loginNative(String access, String refresh) {
        when(appleClient.authenticateNative(anyString(), anyString()))
            .thenReturn(new SocialAuthentication(identity, access, refresh, OAuthClientType.NATIVE));
        OAuthLoginAttemptResult start = nativeLogin.startApple();
        nativeLogin.loginWithApple("code", start.state(), start.loginVerifier(), RefreshTokenDelivery.COOKIE);
    }

    private void assertStored(OAuthClientType expectedType, boolean hasRefresh) {
        EncryptedSocialTokens tokens = socialAccountRepository.findTokens(identity).orElseThrow();
        assertThat(tokens.clientType()).isEqualTo(expectedType);
        assertThat(tokens.accessToken()).isNotNull().startsWith("v1:");
        assertThat(tokens.refreshToken() != null).isEqualTo(hasRefresh);
    }

    private String tokenClientType() {
        return jdbc.queryForObject("SELECT token_client_type FROM social_account WHERE provider = 'APPLE' AND provider_user_id = ?",
            String.class, identity.providerUserId());
    }
}
