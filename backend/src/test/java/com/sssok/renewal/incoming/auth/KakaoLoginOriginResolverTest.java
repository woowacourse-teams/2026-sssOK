package com.sssok.renewal.incoming.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sssok.common.exception.ErrorCode;
import com.sssok.renewal.application.auth.exception.SocialAuthException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;

class KakaoLoginOriginResolverTest {

    @Test
    void Origin이_있으면_정규화한_출처를_반환한다() {
        assertThat(KakaoLoginOriginResolver.resolve(request("Origin", "HTTP://LocalHost:3000"))).isEqualTo("http://localhost:3000");
        assertThat(KakaoLoginOriginResolver.resolve(request("Origin", "https://dev.ssssok.com:443"))).isEqualTo("https://dev.ssssok.com");
    }

    @Test
    void Origin이_없을_때만_Referer의_출처를_쓰고_경로와_쿼리는_버린다() {
        assertThat(KakaoLoginOriginResolver.resolve(request("Referer", "https://dev.ssssok.com/app/login?next=/rooms")))
            .isEqualTo("https://dev.ssssok.com");
    }

    @Test
    void IPv6_축약과_확장_표기를_같은_출처로_비교한다() {
        MockHttpServletRequest request = request("Origin", "https://[::1]");
        request.addHeader("Referer", "https://[0:0:0:0:0:0:0:1]/app/login");
        assertThat(KakaoLoginOriginResolver.resolve(request)).isEqualTo("https://[0:0:0:0:0:0:0:1]");
    }

    @Test
    void Origin과_Referer_출처가_같으면_통과한다() {
        MockHttpServletRequest request = request("Origin", "http://localhost:3000");
        request.addHeader("Referer", "http://localhost:3000/app/login");
        assertThat(KakaoLoginOriginResolver.resolve(request)).isEqualTo("http://localhost:3000");
    }

    @Test
    void Origin과_Referer_출처가_다르면_거절한다() {
        MockHttpServletRequest request = request("Origin", "http://localhost:3000");
        request.addHeader("Referer", "https://dev.ssssok.com/app/login");
        assertInvalid(request);
    }

    @Test
    void Origin이_잘못되면_유효한_Referer로_우회하지_않는다() {
        MockHttpServletRequest request = request("Origin", "null");
        request.addHeader("Referer", "https://dev.ssssok.com/app/login");
        assertInvalid(request);
    }

    @Test
    void Origin이_있으면_형식이_잘못된_Referer도_거절한다() {
        MockHttpServletRequest request = request("Origin", "https://dev.ssssok.com");
        request.addHeader("Referer", "not a url");
        assertInvalid(request);
    }

    @Test
    void 두_헤더가_모두_없으면_거절한다() {
        assertInvalid(new MockHttpServletRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "", " ", "null", "NULL", "*", "localhost:3000", "ftp://localhost:3000", "http://localhost:3000/", "http://localhost:3000/path",
        "http://localhost:3000?x=1", "http://localhost:3000#f", "http://user@localhost:3000", "http://localhost:abc", "https://dev.ssssok.com:",
        "http://localhost:3000.evil.com", "http://localhost:0", "http://localhost:99999", "http://",
        "https://a.example.com, https://b.example.com"
    })
    void 잘못된_Origin은_거절한다(String origin) {
        assertInvalid(request("Origin", origin));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "null", "/relative/path", "https://user:pw@dev.ssssok.com/", "https://dev.ssssok.com/#frag", "mailto:a@b.c", "https://dev.ssssok.com:/app/login"})
    void Origin이_없을_때_잘못된_Referer는_거절한다(String referer) {
        assertInvalid(request("Referer", referer));
    }

    @Test
    void 중복된_Origin_헤더는_거절한다() {
        MockHttpServletRequest request = request("Origin", "http://localhost:3000");
        request.addHeader("Origin", "http://localhost:3000");
        assertInvalid(request);
    }

    @Test
    void 중복된_Referer_헤더는_거절한다() {
        MockHttpServletRequest request = request("Referer", "https://dev.ssssok.com/a");
        request.addHeader("Referer", "https://dev.ssssok.com/b");
        assertInvalid(request);
    }

    private static MockHttpServletRequest request(String name, String value) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(name, value);
        return request;
    }

    private static void assertInvalid(MockHttpServletRequest request) {
        assertThatThrownBy(() -> KakaoLoginOriginResolver.resolve(request)).isInstanceOfSatisfying(SocialAuthException.class,
            e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.INVALID_SOCIAL_AUTH));
    }
}
