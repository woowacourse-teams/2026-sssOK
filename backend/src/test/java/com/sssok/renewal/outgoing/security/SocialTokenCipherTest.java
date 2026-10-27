package com.sssok.renewal.outgoing.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sssok.renewal.application.auth.exception.SocialAuthException;
import com.sssok.renewal.domain.member.SocialIdentity;
import com.sssok.renewal.domain.member.SocialProvider;
import com.sssok.renewal.outgoing.security.SocialTokenCipher.TokenType;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SocialTokenCipherTest {
    private final SocialIdentity identity = new SocialIdentity(SocialProvider.APPLE, "subject");
    private final SocialTokenCipher socialTokenCipher = new SocialTokenCipher(Base64.getEncoder().encodeToString(new byte[32]));

    @Test
    void 같은_토큰도_다른_암호문으로_저장하고_복호화한다() {
        String first = socialTokenCipher.encrypt(identity, TokenType.ACCESS, "secret-token");
        String second = socialTokenCipher.encrypt(identity, TokenType.ACCESS, "secret-token");
        assertThat(first).startsWith("v1:").doesNotContain("secret-token").isNotEqualTo(second);
        assertThat(socialTokenCipher.decrypt(identity, TokenType.ACCESS, first)).isEqualTo("secret-token");
        assertThat(socialTokenCipher.decrypt(identity, TokenType.ACCESS, second)).isEqualTo("secret-token");
    }

    @Test
    void 계정이나_제공자나_토큰종류를_바꾸면_복호화하지_못한다() {
        String stored = socialTokenCipher.encrypt(identity, TokenType.ACCESS, "secret-token");
        assertThatThrownBy(() -> socialTokenCipher.decrypt(new SocialIdentity(SocialProvider.APPLE, "other"), TokenType.ACCESS, stored))
            .isInstanceOf(SocialAuthException.class);
        assertThatThrownBy(() -> socialTokenCipher.decrypt(new SocialIdentity(SocialProvider.KAKAO, "subject"), TokenType.ACCESS, stored))
            .isInstanceOf(SocialAuthException.class);
        assertThatThrownBy(() -> socialTokenCipher.decrypt(identity, TokenType.REFRESH, stored)).isInstanceOf(SocialAuthException.class);
    }

    @Test
    void 암호문이나_키가_바뀌면_원문을_반환하지_않는다() {
        String stored = socialTokenCipher.encrypt(identity, TokenType.ACCESS, "secret-token");
        byte[] payload = Base64.getDecoder().decode(stored.substring(3));
        payload[payload.length - 1] ^= 1;
        String changed = "v1:" + Base64.getEncoder().encodeToString(payload);
        assertThatThrownBy(() -> socialTokenCipher.decrypt(identity, TokenType.ACCESS, changed)).isInstanceOf(SocialAuthException.class)
            .hasNoCause().hasMessageNotContaining("secret-token");
        byte[] anotherKey = new byte[32];
        anotherKey[0] = 1;
        SocialTokenCipher otherCipher = new SocialTokenCipher(Base64.getEncoder().encodeToString(anotherKey));
        assertThatThrownBy(() -> otherCipher.decrypt(identity, TokenType.ACCESS, stored)).isInstanceOf(SocialAuthException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"plain-token", "v2:AAAA", "v1:invalid!", "v1:AAAA"})
    void 손상된_형식은_거부한다(String stored) {
        assertThatThrownBy(() -> socialTokenCipher.decrypt(identity, TokenType.ACCESS, stored)).isInstanceOf(SocialAuthException.class);
    }

    @Test
    void 키가_없으면_평문으로_대체하지_않는다() {
        SocialTokenCipher unavailable = new SocialTokenCipher("");
        assertThatThrownBy(unavailable::requireConfigured).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> unavailable.encrypt(identity, TokenType.ACCESS, "secret-token"))
            .isInstanceOf(SocialAuthException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"bad-key", "YWJjZA=="})
    void 잘못된_키는_값을_노출하지_않고_거부한다(String key) {
        assertThatThrownBy(() -> new SocialTokenCipher(key)).isInstanceOf(IllegalArgumentException.class)
            .hasNoCause().hasMessageNotContaining(key);
    }
}
