package com.sssok.renewal.outgoing.oauth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sssok.common.exception.ErrorCode;
import com.sssok.renewal.application.auth.exception.SocialAuthException;
import com.sssok.renewal.domain.member.SocialIdentity;
import com.sssok.renewal.domain.member.SocialProvider;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import java.io.IOException;
import java.security.KeyPair;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
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


class AppleAuthClientTest {
    private static final KeyPair APPLE_KEY = Jwts.SIG.RS256.keyPair().build();
    private static final KeyPair CLIENT_KEY = Jwts.SIG.ES256.keyPair().build();
    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final OAuthHttpClient oauthHttpClient = new OAuthHttpClient(builder.build());
    private final AppleClientSecretGenerator secret = new AppleClientSecretGenerator("client", "team", "client-key",
        "-----BEGIN PRIVATE KEY-----\n" + Base64.getEncoder().encodeToString(CLIENT_KEY.getPrivate().getEncoded()) + "\n-----END PRIVATE KEY-----");
    private final AppleAuthClient appleAuthClient = new AppleAuthClient(oauthHttpClient, new AppleSigningKeyProvider(oauthHttpClient), secret, "client", "https://example.com/callback");

    @Test
    void 검증된_sub만_사용자_식별자로_사용한다() throws Exception {
        expectToken(token("valid"));
        expectKeys();
        SocialAuthentication result = appleAuthClient.authenticate("code", "server-nonce");
        assertThat(result.identity().provider()).isEqualTo(SocialProvider.APPLE);
        assertThat(result.identity().providerUserId()).isEqualTo("apple-user");
        assertThat(result.refreshToken()).isEqualTo("refresh");
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"issuer", "audience", "nonce", "expired", "signature", "no-expiry", "no-subject", "future-issued"})
    void 변조되거나_로그인_시도와_다른_ID_토큰을_거부한다(String invalid) throws Exception {
        expectToken(token(invalid));
        expectKeys();
        assertThatThrownBy(() -> appleAuthClient.authenticate("code", "server-nonce")).isInstanceOf(SocialAuthException.class);
        server.verify();
    }

    @Test
    void 동일한_공개키_조회는_캐시하고_알수없는_kid는_거부한다() {
        expectKeys();
        AppleSigningKeyProvider keys = new AppleSigningKeyProvider(oauthHttpClient);
        assertThat(keys.findSigningKey("apple-key", "RS256")).isEqualTo(APPLE_KEY.getPublic());
        assertThat(keys.findSigningKey("apple-key", "RS256")).isEqualTo(APPLE_KEY.getPublic());
        assertThatThrownBy(() -> keys.findSigningKey("unknown", "RS256")).isInstanceOf(SocialAuthException.class);
        assertThatThrownBy(() -> keys.findSigningKey("apple-key", "HS256")).isInstanceOf(SocialAuthException.class);
        server.verify();
    }

    @Test
    void client_secret은_지정한_앱과_팀의_짧은_ES256_서명이다() {
        Jws<Claims> signed = Jwts.parser().verifyWith(CLIENT_KEY.getPublic()).build().parseSignedClaims(secret.generate());
        assertThat(signed.getHeader().getAlgorithm()).isEqualTo("ES256");
        assertThat(signed.getHeader().getKeyId()).isEqualTo("client-key");
        assertThat(signed.getPayload().getIssuer()).isEqualTo("team");
        assertThat(signed.getPayload().getSubject()).isEqualTo("client");
        assertThat(signed.getPayload().getAudience()).containsExactly("https://appleid.apple.com");
        assertThat(signed.getPayload().getExpiration().getTime() - signed.getPayload().getIssuedAt().getTime()).isEqualTo(300_000L);
    }

    @Test
    void 서버_nonce가_없으면_코드_교환_전에_거부한다() {
        assertThatThrownBy(() -> appleAuthClient.authenticate("code", null)).isInstanceOf(SocialAuthException.class);
        server.verify();
    }

    private String token(String invalid) {
        Instant now = Instant.now();
        JwtBuilder token = Jwts.builder().header().keyId("apple-key").and()
            .issuer(invalid.equals("issuer") ? "https://attacker.example" : "https://appleid.apple.com")
            .audience().add(invalid.equals("audience") ? "another-client" : "client").and()
            .claim("nonce", invalid.equals("nonce") ? "another-nonce" : "server-nonce")
            .issuedAt(Date.from(invalid.equals("future-issued") ? now.plusSeconds(600) : now.minusSeconds(60)));
        if (!invalid.equals("no-expiry")) { token.expiration(Date.from(invalid.equals("expired") ? now.minusSeconds(10) : now.plusSeconds(300))); }
        if (!invalid.equals("no-subject")) { token.subject("apple-user"); }
        return token.signWith(invalid.equals("signature") ? Jwts.SIG.RS256.keyPair().build().getPrivate() : APPLE_KEY.getPrivate(), Jwts.SIG.RS256).compact();
    }

    private void expectToken(String token) throws Exception {
        String json = new ObjectMapper().createObjectNode().put("id_token", token).put("access_token", "access").put("refresh_token", "refresh").toString();
        server.expect(requestTo("https://appleid.apple.com/auth/token"))
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
            .andExpect(content().string(Matchers.containsString("grant_type=authorization_code")))
            .andExpect(content().string(Matchers.containsString("client_id=client")))
            .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
    }

    private void expectKeys() {
        RSAPublicKey key = (RSAPublicKey) APPLE_KEY.getPublic();
        String json = "{\"keys\":[{\"kid\":\"apple-key\",\"kty\":\"RSA\",\"alg\":\"RS256\",\"use\":\"sig\",\"n\":\""
            + encode(key.getModulus().toByteArray()) + "\",\"e\":\"" + encode(key.getPublicExponent().toByteArray()) + "\"}]}";
        server.expect(requestTo("https://appleid.apple.com/auth/keys"))
            .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
    }

    private String encode(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes[0] == 0 ? Arrays.copyOfRange(bytes, 1, bytes.length) : bytes);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void refresh를_우선_사용하고_없으면_access를_해제한다(boolean hasRefresh) {
        server.expect(requestTo("https://appleid.apple.com/auth/revoke"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
            .andExpect(content().string(Matchers.containsString("client_id=client")))
            .andExpect(content().string(Matchers.containsString("client_secret=")))
            .andExpect(content().string(Matchers.containsString("token=" + (hasRefresh ? "refresh" : "access"))))
            .andExpect(content().string(Matchers.containsString("token_type_hint=" + (hasRefresh ? "refresh_token" : "access_token"))))
            .andRespond(withSuccess());
        appleAuthClient.unlink(new SocialAuthentication(new SocialIdentity(SocialProvider.APPLE, "sub"),
            "access", hasRefresh ? "refresh" : null));
        server.verify();
    }

    @Test
    void invalid_grant는_재인증으로_구분한다() {
        server.expect(requestTo("https://appleid.apple.com/auth/revoke"))
            .andRespond(withBadRequest().body("{\"error\":\"invalid_grant\",\"detail\":\"secret\"}")
                .contentType(MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> appleAuthClient.unlink(unlinkAuthentication())).isInstanceOfSatisfying(SocialAuthException.class,
            e -> { assertThat(e.errorCode()).isEqualTo(ErrorCode.SOCIAL_REAUTHENTICATION_REQUIRED);
                assertThat(e.getMessage()).doesNotContain("secret"); assertThat(e.getCause()).isNull(); });
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(ints = {302, 400, 401, 429, 500})
    void 해제_실패는_성공으로_바꾸거나_재시도하지_않는다(int status) {
        server.expect(requestTo("https://appleid.apple.com/auth/revoke"))
            .andRespond(withStatus(HttpStatus.valueOf(status))
                .body("{\"error\":\"invalid_client\"}").contentType(MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> appleAuthClient.unlink(unlinkAuthentication())).isInstanceOfSatisfying(SocialAuthException.class,
            e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.SOCIAL_AUTH_UNAVAILABLE));
        server.verify();
    }

    @Test
    void 응답을_받지_못하면_성공으로_간주하지_않는다() {
        server.expect(requestTo("https://appleid.apple.com/auth/revoke"))
            .andRespond(withException(new IOException("secret-response")));
        assertThatThrownBy(() -> appleAuthClient.unlink(unlinkAuthentication())).isInstanceOfSatisfying(SocialAuthException.class,
            e -> { assertThat(e.errorCode()).isEqualTo(ErrorCode.SOCIAL_AUTH_UNAVAILABLE);
                assertThat(e.getCause()).isNull(); });
        server.verify();
    }

    private SocialAuthentication unlinkAuthentication() {
        return new SocialAuthentication(new SocialIdentity(SocialProvider.APPLE, "sub"),
            "access", "refresh");
    }
}
