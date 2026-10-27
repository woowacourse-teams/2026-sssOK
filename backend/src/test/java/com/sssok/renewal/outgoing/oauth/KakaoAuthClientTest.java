package com.sssok.renewal.outgoing.oauth;

import com.sssok.common.exception.ErrorCode;
import com.sssok.renewal.application.auth.exception.SocialAuthException;
import com.sssok.renewal.domain.member.SocialIdentity;
import com.sssok.renewal.domain.member.SocialProvider;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;


class KakaoAuthClientTest {
    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final KakaoAuthClient kakaoAuthClient = new KakaoAuthClient(new OAuthHttpClient(builder.build()), "client", "secret", "https://example.com/callback", "admin-key");

    @Test
    void 서버에서_코드를_교환하고_사용자_번호를_식별자로_반환한다() {
        server.expect(requestTo("https://kauth.kakao.com/oauth/token"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
            .andExpect(content().string(Matchers.containsString("code=one-time-code")))
            .andExpect(content().string(Matchers.containsString("client_secret=secret")))
            .andRespond(withSuccess("{\"access_token\":\"access\",\"refresh_token\":\"refresh\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://kapi.kakao.com/v2/user/me"))
            .andExpect(header("Authorization", "Bearer access"))
            .andRespond(withSuccess("{\"id\":1234567890}", MediaType.APPLICATION_JSON));

        SocialAuthentication result = kakaoAuthClient.authenticate("one-time-code", null);
        assertThat(result.identity().provider()).isEqualTo(SocialProvider.KAKAO);
        assertThat(result.identity().providerUserId()).isEqualTo("1234567890");
        assertThat(result.refreshToken()).isEqualTo("refresh");
        assertThat(result.toString()).doesNotContain("access", "refresh");
        server.verify();
    }

    @Test
    void 만료되거나_재사용된_코드는_인증_실패이며_자동_재시도하지_않는다() {
        server.expect(requestTo("https://kauth.kakao.com/oauth/token"))
            .andRespond(withBadRequest().body("{\"error\":\"invalid_grant\",\"error_description\":\"secret-value\"}")
                .contentType(MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> kakaoAuthClient.authenticate("expired", null)).isInstanceOfSatisfying(SocialAuthException.class,
            e -> { assertThat(e.errorCode()).isEqualTo(ErrorCode.INVALID_SOCIAL_AUTH);
                assertThat(e.getMessage()).doesNotContain("secret-value"); assertThat(e.getCause()).isNull(); });
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 429, 500})
    void 제공자_실패를_서비스_장애로_변환한다(int status) {
        server.expect(requestTo("https://kauth.kakao.com/oauth/token"))
            .andRespond(withStatus(HttpStatus.valueOf(status)).body("secret-value"));
        assertThatThrownBy(() -> kakaoAuthClient.authenticate("code", null)).isInstanceOfSatisfying(SocialAuthException.class,
            e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.SOCIAL_AUTH_UNAVAILABLE));
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"access_token\":null}", "[]"})
    void 잘못된_토큰_응답으로_사용자를_조회하지_않는다(String response) {
        server.expect(requestTo("https://kauth.kakao.com/oauth/token"))
            .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> kakaoAuthClient.authenticate("code", null)).isInstanceOf(SocialAuthException.class);
        server.verify();
    }

    @Test
    void 빈_코드는_외부_요청_전에_거부한다() {
        assertThatThrownBy(() -> kakaoAuthClient.authenticate(" ", null)).isInstanceOf(SocialAuthException.class);
        server.verify();
    }

    @Test
    void 연결해제는_어드민_키와_회원번호로_요청한다() {
        server.expect(requestTo("https://kapi.kakao.com/v1/user/unlink"))
            .andExpect(method(HttpMethod.POST)).andExpect(header("Authorization", "KakaoAK admin-key"))
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
            .andExpect(content().string("target_id_type=user_id&target_id=123"))
            .andRespond(withSuccess("{\"id\":123}", MediaType.APPLICATION_JSON));
        kakaoAuthClient.unlink(unlinkAuthentication());
        server.verify();
    }

    @Test
    void 잘못된_어드민_키는_서버_설정_장애이며_재인증을_요구하지_않는다() {
        server.expect(requestTo("https://kapi.kakao.com/v1/user/unlink"))
            .andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("{\"code\":-401,\"msg\":\"secret\"}")
                .contentType(MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> kakaoAuthClient.unlink(unlinkAuthentication())).isInstanceOfSatisfying(SocialAuthException.class,
            e -> { assertThat(e.errorCode()).isEqualTo(ErrorCode.SOCIAL_AUTH_UNAVAILABLE);
                assertThat(e.getMessage()).doesNotContain("secret"); assertThat(e.getCause()).isNull(); });
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"id\":456}", "{\"id\":\"123\"}"})
    void 잘못된_해제_응답을_성공으로_취급하지_않는다(String response) {
        server.expect(requestTo("https://kapi.kakao.com/v1/user/unlink"))
            .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> kakaoAuthClient.unlink(unlinkAuthentication())).isInstanceOf(SocialAuthException.class);
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(ints = {302, 401, 429, 500})
    void 불명확한_연결해제_실패는_장애로_반환한다(int status) {
        server.expect(requestTo("https://kapi.kakao.com/v1/user/unlink"))
            .andRespond(withStatus(HttpStatus.valueOf(status)).body("secret"));
        assertThatThrownBy(() -> kakaoAuthClient.unlink(unlinkAuthentication())).isInstanceOfSatisfying(SocialAuthException.class,
            e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.SOCIAL_AUTH_UNAVAILABLE));
        server.verify();
    }

    private SocialAuthentication unlinkAuthentication() {
        return new SocialAuthentication(new SocialIdentity(SocialProvider.KAKAO, "123"),
            null, null);
    }
}
