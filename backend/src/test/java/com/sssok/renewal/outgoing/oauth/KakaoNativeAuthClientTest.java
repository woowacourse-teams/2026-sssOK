package com.sssok.renewal.outgoing.oauth;

import com.sssok.common.exception.ErrorCode;
import com.sssok.renewal.application.auth.exception.SocialAuthException;
import com.sssok.renewal.domain.member.SocialProvider;
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

class KakaoNativeAuthClientTest {
    private static final String INFO_URL = "https://kapi.kakao.com/v1/user/access_token_info";
    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final KakaoAuthClient client =
        new KakaoAuthClient(new OAuthHttpClient(builder.build()), "client", "secret", "admin", "123456");

    @Test
    void 토큰_정보를_조회해_검증된_사용자_번호만_반환한다() {
        server.expect(requestTo(INFO_URL)).andExpect(method(HttpMethod.GET))
            .andExpect(header("Authorization", "Bearer sdk-token"))
            .andRespond(withSuccess("{\"id\":987654321,\"app_id\":123456,\"expires_in\":3600}", MediaType.APPLICATION_JSON));

        SocialAuthentication result = client.authenticateAccessToken("sdk-token");

        assertThat(result.identity().provider()).isEqualTo(SocialProvider.KAKAO);
        assertThat(result.identity().providerUserId()).isEqualTo("987654321");
        assertThat(result.refreshToken()).isNull();
        assertThat(result.toString()).doesNotContain("sdk-token");
        server.verify();
    }

    @Test
    void 앱_ID가_다르면_인증_실패다() {
        server.expect(requestTo(INFO_URL))
            .andRespond(withSuccess("{\"id\":1,\"app_id\":999,\"expires_in\":3600}", MediaType.APPLICATION_JSON));
        assertInvalid("sdk-token");
        server.verify();
    }

    @Test
    void 만료_시간이_0_이하면_인증_실패다() {
        server.expect(requestTo(INFO_URL))
            .andRespond(withSuccess("{\"id\":1,\"app_id\":123456,\"expires_in\":0}", MediaType.APPLICATION_JSON));
        assertInvalid("sdk-token");
        server.verify();
    }

    @Test
    void 카카오가_401_코드_음수401로_답하면_인증_실패다() {
        server.expect(requestTo(INFO_URL))
            .andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("{\"code\":-401,\"msg\":\"secret-value\"}")
                .contentType(MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.authenticateAccessToken("sdk-token")).isInstanceOfSatisfying(SocialAuthException.class,
            e -> { assertThat(e.errorCode()).isEqualTo(ErrorCode.INVALID_SOCIAL_AUTH);
                assertThat(e.getMessage()).doesNotContain("secret-value"); assertThat(e.getCause()).isNull(); });
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 403, 429, 500})
    void 그_외_제공자_실패는_사용자_토큰_오류로_숨기지_않고_장애로_반환한다(int status) {
        server.expect(requestTo(INFO_URL)).andRespond(withStatus(HttpStatus.valueOf(status)).body("secret"));
        assertUnavailable("sdk-token");
        server.verify();
    }

    @Test
    void 일반_401도_코드가_없으면_장애로_반환한다() {
        server.expect(requestTo(INFO_URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("secret"));
        assertUnavailable("sdk-token");
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{}", "[]", "null",
        "{\"app_id\":123456,\"expires_in\":10}",
        "{\"id\":1,\"expires_in\":10}",
        "{\"id\":1,\"app_id\":123456}",
        "{\"id\":\"1\",\"app_id\":123456,\"expires_in\":10}",
        "{\"id\":1,\"app_id\":\"123456\",\"expires_in\":10}",
        "{\"id\":1,\"app_id\":123456,\"expires_in\":\"10\"}",
        "{\"id\":0,\"app_id\":123456,\"expires_in\":10}",
        "{\"id\":-5,\"app_id\":123456,\"expires_in\":10}",
        "{\"id\":1.5,\"app_id\":123456,\"expires_in\":10}",
        "{\"id\":99999999999999999999,\"app_id\":123456,\"expires_in\":10}",
        "{\"id\":1,\"app_id\":123456,\"expires_in\":99999999999999999999}"
    })
    void 응답_필드가_누락되거나_형식이_틀리면_기본값으로_보정하지_않고_장애다(String response) {
        server.expect(requestTo(INFO_URL)).andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
        assertUnavailable("sdk-token");
        server.verify();
    }

    @Test
    void 앱_ID가_설정되지_않으면_외부_요청_없이_사용할_수_없다() {
        KakaoAuthClient unconfigured = new KakaoAuthClient(new OAuthHttpClient(builder.build()), "c", "s", "a", null);
        assertThatThrownBy(() -> unconfigured.authenticateAccessToken("sdk-token")).isInstanceOfSatisfying(
            SocialAuthException.class, e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.SOCIAL_AUTH_UNAVAILABLE));
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " "})
    void 빈_토큰은_외부_요청_전에_거부한다(String token) {
        assertInvalid(token);
        server.verify();
    }

    private void assertInvalid(String token) {
        assertThatThrownBy(() -> client.authenticateAccessToken(token)).isInstanceOfSatisfying(SocialAuthException.class,
            e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.INVALID_SOCIAL_AUTH));
    }

    private void assertUnavailable(String token) {
        assertThatThrownBy(() -> client.authenticateAccessToken(token)).isInstanceOfSatisfying(SocialAuthException.class,
            e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.SOCIAL_AUTH_UNAVAILABLE));
    }
}
