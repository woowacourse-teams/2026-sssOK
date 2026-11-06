package com.sssok.renewal.outgoing.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AppleOAuthPropertiesTest {
    private static final String REDIRECT = "https://example.com/callback";

    private AppleOAuthProperties properties(String nativeClientId) {
        return new AppleOAuthProperties("web.client", "team", "key", "private", REDIRECT, nativeClientId);
    }

    @Test
    void 앱_client가_없어도_웹_설정은_유효하다() {
        AppleOAuthProperties web = new AppleOAuthProperties("web.client", "team", "key", "private", REDIRECT, null);
        web.validate();
        assertThat(web.nativeLoginConfigured()).isFalse();
        assertThat(properties("  ").nativeLoginConfigured()).isFalse();
    }

    @Test
    void 앱_client를_공백_제거해_활성화한다() {
        AppleOAuthProperties withNative = properties(" com.example.app ");
        withNative.validate();
        assertThat(withNative.nativeLoginConfigured()).isTrue();
        assertThat(withNative.nativeClientId()).isEqualTo("com.example.app");
    }

    @Test
    void 웹_client와_같은_앱_client는_검증에_실패한다() {
        assertThatThrownBy(() -> properties("web.client").validate()).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"com.example app", "com.example\napp"})
    void 공백이나_제어_문자가_있는_앱_client는_검증에_실패한다(String nativeClientId) {
        assertThatThrownBy(() -> properties(nativeClientId).validate()).isInstanceOf(IllegalArgumentException.class);
    }
}
