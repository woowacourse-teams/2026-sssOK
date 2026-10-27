package com.sssok.renewal.application.auth;

import com.sssok.common.exception.ErrorCode;
import com.sssok.renewal.application.auth.exception.SocialAuthException;
import com.sssok.renewal.domain.auth.EncryptedSocialTokens;
import com.sssok.renewal.domain.auth.RefreshTokenDelivery;
import com.sssok.renewal.domain.member.SocialIdentity;
import com.sssok.renewal.domain.member.SocialProvider;
import com.sssok.renewal.outgoing.database.member.SocialAccountRepository;
import com.sssok.renewal.outgoing.oauth.AppleAuthClient;
import com.sssok.renewal.outgoing.oauth.KakaoAuthClient;
import com.sssok.renewal.outgoing.oauth.SocialAuthClient;
import com.sssok.renewal.outgoing.oauth.SocialAuthentication;
import com.sssok.support.PostgresContainerSupport;
import com.sssok.support.PostgresIntegrationTest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.NullSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;


@PostgresIntegrationTest
class UnlinkSocialAccountServiceTest extends PostgresContainerSupport {
    @Autowired UnlinkSocialAccountService unlinkSocialAccountService;
    @Autowired SocialMemberLoginService socialMemberLoginService;
    @Autowired SessionTokenProvider sessionTokenProvider;
    @Autowired SocialAccountRepository socialAccountRepository;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired PlatformTransactionManager transactionManager;
    @MockitoBean KakaoAuthClient kakao;
    @MockitoBean AppleAuthClient apple;

    @BeforeEach
    void setUp() {
        when(kakao.provider()).thenReturn(SocialProvider.KAKAO);
        when(apple.provider()).thenReturn(SocialProvider.APPLE);
    }

    @ParameterizedTest
    @EnumSource(SocialProvider.class)
    void 제공자별_인증으로_연결을_해제하고_로컬_상태는_유지한다(SocialProvider provider) {
        SocialIdentity identity = identity(provider);
        SocialAuthentication authentication = new SocialAuthentication(identity, "plain-access", "plain-refresh");
        AuthResult registered = socialMemberLoginService.login(authentication, List.of(), RefreshTokenDelivery.COOKIE);
        EncryptedSocialTokens stored = socialAccountRepository.findTokens(identity).orElseThrow();
        SocialAuthClient client = provider == SocialProvider.KAKAO ? kakao : apple;
        SocialAuthentication unlinkAuthentication = provider == SocialProvider.KAKAO
            ? new SocialAuthentication(identity, null, null) : authentication;
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat((SocialAuthentication) invocation.getArgument(0)).isEqualTo(unlinkAuthentication);
            return null;
        }).when(client).unlink(any());
        unlinkSocialAccountService.unlink(identity);
        verify(client).unlink(unlinkAuthentication);
        assertThat(socialAccountRepository.findTokens(identity).orElseThrow()).isEqualTo(stored);
        assertThat(sessionTokenProvider.parse(registered.accessToken())).isEqualTo(registered.userId());
    }

    @Test
    void 애플_토큰이_없는_이전_계정은_재인증이_필요하다() {
        SocialIdentity identity = identity(SocialProvider.APPLE);
        socialMemberLoginService.login(new SocialAuthentication(identity, "access", null), List.of(), RefreshTokenDelivery.COOKIE);
        jdbcTemplate.update("UPDATE social_account SET encrypted_access_token = NULL, encrypted_refresh_token = NULL WHERE provider_user_id = ?",
            identity.providerUserId());
        assertThatThrownBy(() -> unlinkSocialAccountService.unlink(identity)).isInstanceOfSatisfying(SocialAuthException.class,
            e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.SOCIAL_REAUTHENTICATION_REQUIRED));
        verify(apple, never()).unlink(any());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"v1:broken"})
    void 카카오_연결해제는_누락되거나_손상된_보관_토큰을_사용하지_않는다(String storedAccessToken) {
        SocialIdentity identity = identity(SocialProvider.KAKAO);
        socialMemberLoginService.login(new SocialAuthentication(identity, "access", null), List.of(), RefreshTokenDelivery.COOKIE);
        jdbcTemplate.update("UPDATE social_account SET encrypted_access_token = ? WHERE provider_user_id = ?",
            storedAccessToken, identity.providerUserId());

        unlinkSocialAccountService.unlink(identity);

        verify(kakao).unlink(new SocialAuthentication(identity, null, null));
    }

    @Test
    void 외부_실패시_토큰과_서비스_인증을_삭제하지_않는다() {
        SocialIdentity identity = identity(SocialProvider.KAKAO);
        AuthResult registered = socialMemberLoginService.login(new SocialAuthentication(identity, "access", "refresh"), List.of(), RefreshTokenDelivery.COOKIE);
        EncryptedSocialTokens stored = socialAccountRepository.findTokens(identity).orElseThrow();
        doThrow(SocialAuthException.unavailable()).when(kakao).unlink(any());
        assertThatThrownBy(() -> unlinkSocialAccountService.unlink(identity)).isInstanceOf(SocialAuthException.class);
        assertThat(socialAccountRepository.findTokens(identity).orElseThrow()).isEqualTo(stored);
        assertThat(sessionTokenProvider.parse(registered.accessToken())).isEqualTo(registered.userId());
        verify(kakao, times(1)).unlink(any());
    }

    @Test
    void 손상된_애플_암호문은_외부로_전달하지_않는다() {
        SocialIdentity identity = identity(SocialProvider.APPLE);
        socialMemberLoginService.login(new SocialAuthentication(identity, "access", "refresh"), List.of(), RefreshTokenDelivery.COOKIE);
        jdbcTemplate.update("UPDATE social_account SET encrypted_access_token = 'v1:broken' WHERE provider_user_id = ?",
            identity.providerUserId());
        assertThatThrownBy(() -> unlinkSocialAccountService.unlink(identity)).isInstanceOfSatisfying(SocialAuthException.class,
            e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.SOCIAL_AUTH_UNAVAILABLE));
        verify(apple, never()).unlink(any());
    }

    @Test
    void DB_트랜잭션_안에서는_외부_연결해제를_실행하지_않는다() {
        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status ->
            unlinkSocialAccountService.unlink(identity(SocialProvider.APPLE)))).isInstanceOf(IllegalTransactionStateException.class);
        verify(apple, never()).unlink(any());
    }

    private SocialIdentity identity(SocialProvider provider) {
        return new SocialIdentity(provider, UUID.randomUUID().toString());
    }
}
