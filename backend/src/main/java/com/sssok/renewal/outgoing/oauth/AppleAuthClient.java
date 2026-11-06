package com.sssok.renewal.outgoing.oauth;

import com.fasterxml.jackson.databind.JsonNode;
import com.sssok.renewal.application.auth.exception.SocialAuthException;
import com.sssok.renewal.domain.auth.OAuthClientType;
import com.sssok.renewal.domain.member.SocialIdentity;
import com.sssok.renewal.domain.member.SocialProvider;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwsHeader;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import java.time.Duration;
import java.time.Instant;
import org.springframework.util.LinkedMultiValueMap;

// 같은 애플 계정이라도 웹 로그인과 앱 SDK 로그인은 서로 다른 client(식별자·client_secret)를 쓴다.
// 코드 교환·ID 토큰 audience·연결 해제 모두 토큰을 발급한 쪽의 client로 처리한다.
public class AppleAuthClient implements SocialAuthClient {
    private static final String TOKEN_ENDPOINT = "https://appleid.apple.com/auth/token";
    private static final String REVOKE_ENDPOINT = "https://appleid.apple.com/auth/revoke";
    private static final String TOKEN_ISSUER = "https://appleid.apple.com";
    private static final Duration ALLOWED_ISSUED_AT_CLOCK_SKEW = Duration.ofSeconds(60);
    private static final int MAX_SUBJECT_LENGTH = 255;

    private record Client(String id, AppleClientSecretGenerator secretGenerator) {}

    private final OAuthHttpClient oauthHttpClient;
    private final AppleSigningKeyProvider appleSigningKeyProvider;
    private final Client webClient;
    private final String redirectUri;
    // 앱 SDK 로그인을 쓰지 않으면 null이다.
    private final Client nativeClient;

    public AppleAuthClient(
        OAuthHttpClient oauthHttpClient,
        AppleSigningKeyProvider appleSigningKeyProvider,
        AppleClientSecretGenerator clientSecretGenerator,
        String clientId,
        String redirectUri,
        String nativeClientId,
        AppleClientSecretGenerator nativeClientSecretGenerator
    ) {
        this.oauthHttpClient = oauthHttpClient;
        this.appleSigningKeyProvider = appleSigningKeyProvider;
        this.webClient = new Client(clientId, clientSecretGenerator);
        this.redirectUri = redirectUri;
        boolean nativeConfigured = nativeClientId != null && !nativeClientId.isBlank() && nativeClientSecretGenerator != null;
        this.nativeClient = nativeConfigured ? new Client(nativeClientId, nativeClientSecretGenerator) : null;
    }

    @Override
    public SocialProvider provider() {
        return SocialProvider.APPLE;
    }

    public boolean nativeConfigured() {
        return nativeClient != null;
    }

    @Override
    public SocialAuthentication authenticate(String authorizationCode, String expectedNonce, String ignoredStoredRedirectUri) {
        validateAuthenticationRequest(authorizationCode, expectedNonce);
        return exchangeAndVerify(authorizationCode, expectedNonce, OAuthClientType.WEB);
    }

    // 앱 SDK가 반환한 인가 코드를 앱 client로 교환한다. 앱 SDK가 발급한 코드에는 웹 redirect_uri가 없으므로 보내지 않고,
    // ID 토큰의 audience도 앱 client 식별자여야 한다.
    public SocialAuthentication authenticateNative(String authorizationCode, String expectedNonce) {
        if (!nativeConfigured()) {
            throw SocialAuthException.unavailable();
        }
        validateAuthenticationRequest(authorizationCode, expectedNonce);
        return exchangeAndVerify(authorizationCode, expectedNonce, OAuthClientType.NATIVE);
    }

    @Override
    public void unlink(SocialAuthentication authentication) {
        if (authentication.identity().provider() != provider()) {
            throw SocialAuthException.invalid();
        }
        boolean hasRefreshToken = authentication.refreshToken() != null && !authentication.refreshToken().isBlank();
        String token = hasRefreshToken ? authentication.refreshToken() : authentication.accessToken();
        if (token == null || token.isBlank()) {
            throw SocialAuthException.reauthenticationRequired();
        }
        // 토큰을 발급한 client로만 철회한다. 앱 client가 지금 없으면 웹 client로 대신하지 않는다.
        Client client = clientOf(authentication.clientType());
        LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", client.id());
        form.add("client_secret", client.secretGenerator().generate());
        form.add("token", token);
        form.add("token_type_hint", hasRefreshToken ? "refresh_token" : "access_token");
        oauthHttpClient.postRevocation(REVOKE_ENDPOINT, form);
    }

    private Client clientOf(OAuthClientType clientType) {
        if (clientType == OAuthClientType.WEB) {
            return webClient;
        }
        if (nativeClient == null) {
            throw SocialAuthException.unavailable();
        }
        return nativeClient;
    }

    private SocialAuthentication exchangeAndVerify(String authorizationCode, String expectedNonce,
                                                   OAuthClientType clientType) {
        Client client = clientOf(clientType);
        JsonNode tokenResponse = exchangeAuthorizationCode(authorizationCode, client, clientType);
        String idToken = OAuthHttpClient.readRequiredText(tokenResponse, "id_token");
        String providerUserId = verifyIdTokenAndGetSubject(idToken, expectedNonce, client.id());

        return new SocialAuthentication(
            new SocialIdentity(provider(), providerUserId),
            OAuthHttpClient.readRequiredText(tokenResponse, "access_token"),
            tokenResponse.path("refresh_token").asText(null),
            clientType
        );
    }

    private void validateAuthenticationRequest(String authorizationCode, String expectedNonce) {
        if (authorizationCode == null || authorizationCode.isBlank()
            || expectedNonce == null || expectedNonce.isBlank()) {
            throw SocialAuthException.invalid();
        }
    }

    private JsonNode exchangeAuthorizationCode(String authorizationCode, Client client, OAuthClientType clientType) {
        LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", client.id());
        form.add("client_secret", client.secretGenerator().generate());
        if (clientType == OAuthClientType.WEB) {
            form.add("redirect_uri", redirectUri);
        }
        form.add("code", authorizationCode);
        return oauthHttpClient.postForm(TOKEN_ENDPOINT, form);
    }

    private String verifyIdTokenAndGetSubject(String idToken, String expectedNonce, String expectedAudience) {
        try {
            Claims claims = Jwts.parser()
                .keyLocator(header -> {
                    if (!(header instanceof JwsHeader signedHeader)) {
                        throw SocialAuthException.invalid();
                    }
                    return appleSigningKeyProvider.findSigningKey(signedHeader.getKeyId(), signedHeader.getAlgorithm());
                })
                .requireIssuer(TOKEN_ISSUER)
                .requireAudience(expectedAudience)
                .require("nonce", expectedNonce)
                .build()
                .parseSignedClaims(idToken)
                .getPayload();

            validateRequiredClaims(claims);
            return claims.getSubject();
        } catch (JwtException | IllegalArgumentException e) {
            throw SocialAuthException.invalid();
        }
    }

    private void validateRequiredClaims(Claims claims) {
        if (claims.getExpiration() == null || claims.getIssuedAt() == null) {
            throw SocialAuthException.invalid();
        }
        if (claims.getIssuedAt().toInstant().isAfter(Instant.now().plus(ALLOWED_ISSUED_AT_CLOCK_SKEW))) {
            throw SocialAuthException.invalid();
        }
        String subject = claims.getSubject();
        if (subject == null || subject.isBlank() || subject.length() > MAX_SUBJECT_LENGTH) {
            throw SocialAuthException.invalid();
        }
    }
}
