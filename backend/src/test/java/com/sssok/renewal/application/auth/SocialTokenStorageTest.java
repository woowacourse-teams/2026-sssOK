package com.sssok.renewal.application.auth;

import com.sssok.renewal.domain.auth.EncryptedSocialTokens;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sssok.renewal.domain.member.SocialIdentity;
import com.sssok.renewal.domain.member.SocialProvider;
import com.sssok.renewal.outgoing.database.member.SocialAccountRepository;
import com.sssok.renewal.outgoing.oauth.SocialAuthentication;
import com.sssok.renewal.outgoing.security.SocialTokenCipher;
import com.sssok.renewal.outgoing.security.SocialTokenCipher.TokenType;
import com.sssok.support.PostgresContainerSupport;
import com.sssok.support.PostgresIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@PostgresIntegrationTest
class SocialTokenStorageTest extends PostgresContainerSupport {
    @Autowired SocialMemberLoginService socialMemberLoginService;
    @Autowired SocialAccountRepository socialAccountRepository;
    @Autowired SocialTokenCipher socialTokenCipher;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired PlatformTransactionManager transactionManager;

    @Test
    void DB에_암호문만_저장하고_탈퇴_연결해제에_사용할_원문을_복원한다() {
        SocialIdentity identity = identity();
        socialMemberLoginService.login(new SocialAuthentication(identity, "secret-access", "secret-refresh"), null);
        EncryptedSocialTokens stored = socialAccountRepository.findTokens(identity).orElseThrow();
        assertThat(stored.accessToken()).startsWith("v1:").doesNotContain("secret-access");
        assertThat(stored.refreshToken()).startsWith("v1:").doesNotContain("secret-refresh");
        assertThat(socialTokenCipher.decrypt(identity, TokenType.ACCESS, stored.accessToken())).isEqualTo("secret-access");
        assertThat(socialTokenCipher.decrypt(identity, TokenType.REFRESH, stored.refreshToken())).isEqualTo("secret-refresh");
        assertThat(stored.toString()).doesNotContain(stored.accessToken(), stored.refreshToken());
        assertThat(jdbcTemplate.queryForObject("SELECT encrypted_access_token FROM social_account WHERE provider_user_id = ?",
            String.class, identity.providerUserId())).isEqualTo(stored.accessToken());
    }

    @Test
    void 새_refresh가_없으면_기존값을_유지하고_새값이_오면_교체한다() {
        SocialIdentity identity = identity();
        socialMemberLoginService.login(new SocialAuthentication(identity, "access-1", "refresh-1"), null);
        socialMemberLoginService.login(new SocialAuthentication(identity, "access-2", null), null);
        EncryptedSocialTokens preserved = socialAccountRepository.findTokens(identity).orElseThrow();
        assertThat(socialTokenCipher.decrypt(identity, TokenType.ACCESS, preserved.accessToken())).isEqualTo("access-2");
        assertThat(socialTokenCipher.decrypt(identity, TokenType.REFRESH, preserved.refreshToken())).isEqualTo("refresh-1");
        socialMemberLoginService.login(new SocialAuthentication(identity, "access-3", "refresh-3"), null);
        EncryptedSocialTokens replaced = socialAccountRepository.findTokens(identity).orElseThrow();
        assertThat(socialTokenCipher.decrypt(identity, TokenType.REFRESH, replaced.refreshToken())).isEqualTo("refresh-3");
    }

    @Test
    void refresh가_없는_최초_인증도_저장할_수_있다() {
        SocialIdentity identity = identity();
        socialMemberLoginService.login(new SocialAuthentication(identity, "access", null), null);
        assertThat(socialAccountRepository.findTokens(identity).orElseThrow().refreshToken()).isNull();
    }

    @Test
    void 계정_메타데이터_저장으로_암호문을_지우지_않는다() {
        SocialIdentity identity = identity();
        socialMemberLoginService.login(new SocialAuthentication(identity, "access", "refresh"), null);
        EncryptedSocialTokens before = socialAccountRepository.findTokens(identity).orElseThrow();
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
            socialAccountRepository.save(socialAccountRepository.findByIdentity(identity).orElseThrow()));
        assertThat(socialAccountRepository.findTokens(identity).orElseThrow()).isEqualTo(before);
    }

    @Test
    void 실패한_로그인은_새_토큰을_남기지_않는다() {
        SocialIdentity identity = identity();
        socialMemberLoginService.login(new SocialAuthentication(identity, "access-1", "refresh-1"), null);
        EncryptedSocialTokens before = socialAccountRepository.findTokens(identity).orElseThrow();
        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            socialMemberLoginService.login(new SocialAuthentication(identity, "access-2", "refresh-2"), null);
            throw new IllegalStateException("실패");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(socialAccountRepository.findTokens(identity).orElseThrow()).isEqualTo(before);
    }

    private SocialIdentity identity() {
        return new SocialIdentity(SocialProvider.APPLE, UUID.randomUUID().toString());
    }
}
