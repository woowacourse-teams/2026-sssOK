package com.sssok.renewal.outgoing.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sssok.renewal.domain.auth.WebOrigin;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class KakaoOAuthPropertiesTest {
    private static final String DEV = "https://dev.ssssok.com/app/auth/callback/kakao";
    private static final String LOCAL = "http://localhost:3000/app/auth/callback/kakao";

    private static KakaoOAuthProperties properties(String... uris) {
        return new KakaoOAuthProperties("client", "secret", Arrays.asList(uris), "admin");
    }

    @Test
    void 출처가_정확히_일치하는_등록_콜백만_선택한다() {
        KakaoOAuthProperties properties = properties(DEV, LOCAL);
        assertThat(properties.callbackFor("https://dev.ssssok.com")).contains(DEV);
        assertThat(properties.callbackFor("http://localhost:3000")).contains(LOCAL);
        assertThat(properties.callbackFor("http://localhost:3001")).isEmpty();
        assertThat(properties.callbackFor("https://localhost:3000")).isEmpty();
        assertThat(properties.callbackFor("https://www.ssssok.com")).isEmpty();
        assertThat(properties.callbackFor(null)).isEmpty();
    }

    @Test
    void IPv6_표기가_달라도_등록한_전체_콜백을_선택하고_중복_출처는_거절한다() {
        String callback = "https://[0:0:0:0:0:0:0:1]/cb";
        assertThat(properties(callback).callbackFor(WebOrigin.parseOrigin("https://[::1]"))).contains(callback);
        assertThatThrownBy(() -> properties(callback, "https://[::1]/other").validate())
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 운영_매핑에_없는_localhost는_선택할_수_없다() {
        assertThat(properties("https://www.ssssok.com/app/auth/callback/kakao").callbackFor("http://localhost:3000")).isEmpty();
    }

    @Test
    void 저장된_콜백이_허용_목록에_있는지_전체_주소로_확인한다() {
        KakaoOAuthProperties properties = properties(DEV, LOCAL);
        assertThat(properties.isAllowedCallback(DEV)).isTrue();
        assertThat(properties.isAllowedCallback(DEV + "?x=1")).isFalse();
        assertThat(properties.isAllowedCallback("https://dev.ssssok.com")).isFalse();
        assertThat(properties.isAllowedCallback(null)).isFalse();
    }

    @Test
    void 유효한_설정은_검증을_통과하고_빈_값과_공백은_무시한다() {
        properties(DEV, " ", LOCAL).validate();
        assertThat(properties(DEV, " ", null).redirectUris()).containsExactly(DEV);
        assertThat(new KakaoOAuthProperties("c", "s", null, "a").redirectUris()).isEmpty();
    }

    @Test
    void 콜백이_하나도_없으면_검증에_실패한다() {
        assertThatThrownBy(() -> new KakaoOAuthProperties("c", "s", List.of(), "a").validate())
            .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "http://example.com/cb", "http://localhost.evil.com/cb", "http://127.0.0.1:3000/cb", "http://[::1]:3000/cb",
        "https://user@example.com/cb", "https://example.com/cb#frag", "https://example.com", "ftp://example.com/cb",
        "/relative", "https://*.example.com/cb", "https://example.com:99999/cb", "https://example.com:/cb", "https://[fe80::1%25en0]/cb"
    })
    void 허용하지_않는_콜백_주소는_검증에_실패한다(String uri) {
        assertThatThrownBy(() -> properties(uri).validate()).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 같은_출처의_콜백이_중복되면_검증에_실패한다() {
        assertThatThrownBy(() -> properties(DEV, "https://dev.ssssok.com/other").validate())
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> properties(DEV, "HTTPS://DEV.ssssok.com:443/other").validate())
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 정규화한_출처가_키가_되어_기본_포트와_대소문자를_같게_본다() {
        assertThat(properties("HTTPS://Dev.SSSSOK.com:443/cb").callbackFor("https://dev.ssssok.com")).contains("HTTPS://Dev.SSSSOK.com:443/cb");
    }
}
