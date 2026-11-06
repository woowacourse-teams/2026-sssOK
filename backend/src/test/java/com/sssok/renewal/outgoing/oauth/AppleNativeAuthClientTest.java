package com.sssok.renewal.outgoing.oauth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sssok.common.exception.ErrorCode;
import com.sssok.renewal.application.auth.exception.SocialAuthException;
import com.sssok.renewal.domain.auth.OAuthClientType;
import com.sssok.renewal.domain.member.SocialIdentity;
import com.sssok.renewal.domain.member.SocialProvider;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.HexFormat;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class AppleNativeAuthClientTest {
    private static final String WEB_CLIENT = "web.client";
    private static final String NATIVE_CLIENT = "com.example.app";
    private static final String NONCE = "server-nonce";
    private static final KeyPair APPLE_KEY = Jwts.SIG.RS256.keyPair().build();
    private static final KeyPair CLIENT_KEY = Jwts.SIG.ES256.keyPair().build();
    private static final String PEM = "-----BEGIN PRIVATE KEY-----\n"
        + Base64.getEncoder().encodeToString(CLIENT_KEY.getPrivate().getEncoded()) + "\n-----END PRIVATE KEY-----";

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final OAuthHttpClient oauthHttpClient = new OAuthHttpClient(builder.build());
    private final AppleAuthClient client = new AppleAuthClient(oauthHttpClient, new AppleSigningKeyProvider(oauthHttpClient),
        new AppleClientSecretGenerator(WEB_CLIENT, "team", "key", PEM), WEB_CLIENT, "https://example.com/callback",
        NATIVE_CLIENT, new AppleClientSecretGenerator(NATIVE_CLIENT, "team", "key", PEM));

    @Test
    void 앱_client로_교환하고_웹_redirect_uri_없이_검증된_sub만_반환한다() throws Exception {
        expectNativeExchange(idToken(NATIVE_CLIENT, NONCE, "valid"));
        expectKeys();

        SocialAuthentication result = client.authenticateNative("sdk-code", NONCE);

        assertThat(result.identity()).isEqualTo(new SocialIdentity(SocialProvider.APPLE, "apple-user"));
        assertThat(result.clientType()).isEqualTo(OAuthClientType.NATIVE);
        assertThat(result.refreshToken()).isEqualTo("refresh");
        assertThat(result.toString()).doesNotContain("access", "refresh");
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"issuer", "audience-web", "audience-other", "nonce", "expired", "signature", "no-expiry",
        "no-subject", "future-issued", "nonce-hashed"})
    void 변조되거나_앱_시도와_다른_ID_토큰을_거부한다(String invalid) throws Exception {
        expectNativeExchange(idToken(invalid.startsWith("audience") ? ("audience-web".equals(invalid) ? WEB_CLIENT : "x") : NATIVE_CLIENT,
            NONCE, invalid));
        expectKeys();
        assertThatThrownBy(() -> client.authenticateNative("sdk-code", NONCE)).isInstanceOfSatisfying(
            SocialAuthException.class, e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.INVALID_SOCIAL_AUTH));
        server.verify();
    }

    @Test
    void 웹_audience로_발급된_토큰은_웹_로그인에서만_받아들인다() throws Exception {
        expectNativeExchange(idToken(WEB_CLIENT, NONCE, "valid"));
        expectKeys();
        assertThatThrownBy(() -> client.authenticateNative("sdk-code", NONCE)).isInstanceOf(SocialAuthException.class);
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " "})
    void 빈_코드나_nonce는_코드_교환_전에_거부한다(String blank) {
        assertThatThrownBy(() -> client.authenticateNative(blank, NONCE)).isInstanceOf(SocialAuthException.class);
        assertThatThrownBy(() -> client.authenticateNative("code", blank)).isInstanceOf(SocialAuthException.class);
        assertThatThrownBy(() -> client.authenticateNative("code", null)).isInstanceOf(SocialAuthException.class);
        server.verify();
    }

    @Test
    void 앱_client가_설정되지_않으면_외부_요청_없이_사용할_수_없다() {
        AppleAuthClient webOnly = new AppleAuthClient(oauthHttpClient, new AppleSigningKeyProvider(oauthHttpClient),
            new AppleClientSecretGenerator(WEB_CLIENT, "team", "key", PEM), WEB_CLIENT, "https://example.com/callback", null, null);
        assertThat(webOnly.nativeConfigured()).isFalse();
        assertThat(client.nativeConfigured()).isTrue();
        assertThatThrownBy(() -> webOnly.authenticateNative("code", NONCE)).isInstanceOfSatisfying(
            SocialAuthException.class, e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.SOCIAL_AUTH_UNAVAILABLE));
        server.verify();
    }

    @Test
    void 웹_로그인은_앱_설정이_있어도_웹_client와_redirect_uri를_쓴다() throws Exception {
        server.expect(requestTo("https://appleid.apple.com/auth/token"))
            .andExpect(content().string(Matchers.containsString("client_id=" + WEB_CLIENT)))
            .andExpect(content().string(Matchers.containsString("redirect_uri=https%3A%2F%2Fexample.com%2Fcallback")))
            .andRespond(withSuccess(tokenJson(idToken(WEB_CLIENT, NONCE, "valid")), MediaType.APPLICATION_JSON));
        expectKeys();
        SocialAuthentication result = client.authenticate("code", NONCE, "ignored");
        assertThat(result.clientType()).isEqualTo(OAuthClientType.WEB);
        server.verify();
    }

    @Test
    void 해제는_토큰을_발급한_앱_client_설정으로_철회한다() throws Exception {
        server.expect(requestTo("https://appleid.apple.com/auth/revoke")).andExpect(method(HttpMethod.POST))
            .andExpect(request -> {
                String body = ((MockClientHttpRequest) request).getBodyAsString();
                assertThat(body).contains("client_id=" + NATIVE_CLIENT, "token=refresh", "token_type_hint=refresh_token");
                assertThat(secretSubject(body)).isEqualTo(NATIVE_CLIENT);
            })
            .andRespond(withSuccess());
        client.unlink(new SocialAuthentication(new SocialIdentity(SocialProvider.APPLE, "sub"), "access", "refresh",
            OAuthClientType.NATIVE));
        server.verify();
    }

    @Test
    void 해제는_웹_토큰을_웹_client_설정으로_철회한다() throws Exception {
        server.expect(requestTo("https://appleid.apple.com/auth/revoke"))
            .andExpect(request -> {
                String body = ((MockClientHttpRequest) request).getBodyAsString();
                assertThat(body).contains("client_id=" + WEB_CLIENT);
                assertThat(secretSubject(body)).isEqualTo(WEB_CLIENT);
            })
            .andRespond(withSuccess());
        client.unlink(new SocialAuthentication(new SocialIdentity(SocialProvider.APPLE, "sub"), "access", "refresh"));
        server.verify();
    }

    @Test
    void 앱_토큰의_해제는_앱_설정이_없으면_웹_설정으로_대신하지_않고_장애로_반환한다() {
        AppleAuthClient webOnly = new AppleAuthClient(oauthHttpClient, new AppleSigningKeyProvider(oauthHttpClient),
            new AppleClientSecretGenerator(WEB_CLIENT, "team", "key", PEM), WEB_CLIENT, "https://example.com/callback", null, null);
        assertThatThrownBy(() -> webOnly.unlink(new SocialAuthentication(new SocialIdentity(SocialProvider.APPLE, "sub"),
            "access", "refresh", OAuthClientType.NATIVE))).isInstanceOfSatisfying(SocialAuthException.class,
            e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.SOCIAL_AUTH_UNAVAILABLE));
        server.verify();
    }

    private String secretSubject(String formBody) {
        String secret = Arrays.stream(formBody.split("&")).filter(part -> part.startsWith("client_secret="))
            .map(part -> URLDecoder.decode(part.substring("client_secret=".length()), StandardCharsets.UTF_8)).findFirst().orElseThrow();
        Claims claims = Jwts.parser().verifyWith(CLIENT_KEY.getPublic()).build().parseSignedClaims(secret).getPayload();
        return claims.getSubject();
    }

    private void expectNativeExchange(String idToken) throws Exception {
        server.expect(requestTo("https://appleid.apple.com/auth/token")).andExpect(method(HttpMethod.POST))
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
            .andExpect(content().string(Matchers.containsString("grant_type=authorization_code")))
            .andExpect(content().string(Matchers.containsString("client_id=" + NATIVE_CLIENT)))
            .andExpect(content().string(Matchers.containsString("code=sdk-code")))
            .andExpect(content().string(Matchers.not(Matchers.containsString("redirect_uri"))))
            .andExpect(request -> assertThat(secretSubject(((MockClientHttpRequest) request).getBodyAsString())).isEqualTo(NATIVE_CLIENT))
            .andRespond(withSuccess(tokenJson(idToken), MediaType.APPLICATION_JSON));
    }

    private String tokenJson(String idToken) throws Exception {
        return new ObjectMapper().createObjectNode().put("id_token", idToken).put("access_token", "access")
            .put("refresh_token", "refresh").toString();
    }

    private String idToken(String audience, String nonce, String invalid) throws Exception {
        Instant now = Instant.now();
        String nonceClaim = switch (invalid) {
            case "nonce" -> "another-nonce";
            // 서버 nonce의 SHA-256 해시를 돌려주는 SDK 래퍼가 있어도 raw/hash를 둘 다 허용하지 않는다.
            case "nonce-hashed" -> HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(nonce.getBytes(StandardCharsets.UTF_8)));
            default -> nonce;
        };
        JwtBuilder token = Jwts.builder().header().keyId("apple-key").and()
            .issuer(invalid.equals("issuer") ? "https://attacker.example" : "https://appleid.apple.com")
            .audience().add(audience).and()
            .claim("nonce", nonceClaim)
            .issuedAt(Date.from(invalid.equals("future-issued") ? now.plusSeconds(600) : now.minusSeconds(60)));
        if (!invalid.equals("no-expiry")) { token.expiration(Date.from(invalid.equals("expired") ? now.minusSeconds(10) : now.plusSeconds(300))); }
        if (!invalid.equals("no-subject")) { token.subject("apple-user"); }
        return token.signWith(invalid.equals("signature") ? Jwts.SIG.RS256.keyPair().build().getPrivate() : APPLE_KEY.getPrivate(), Jwts.SIG.RS256).compact();
    }

    private void expectKeys() {
        RSAPublicKey key = (RSAPublicKey) APPLE_KEY.getPublic();
        String json = "{\"keys\":[{\"kid\":\"apple-key\",\"kty\":\"RSA\",\"alg\":\"RS256\",\"use\":\"sig\",\"n\":\""
            + encode(key.getModulus().toByteArray()) + "\",\"e\":\"" + encode(key.getPublicExponent().toByteArray()) + "\"}]}";
        server.expect(requestTo("https://appleid.apple.com/auth/keys")).andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
    }

    private String encode(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes[0] == 0 ? Arrays.copyOfRange(bytes, 1, bytes.length) : bytes);
    }
}
