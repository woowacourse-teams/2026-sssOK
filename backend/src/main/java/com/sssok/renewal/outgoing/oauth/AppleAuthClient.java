package com.sssok.renewal.outgoing.oauth;

import com.fasterxml.jackson.databind.JsonNode;
import com.sssok.renewal.application.auth.exception.SocialAuthException;
import com.sssok.renewal.domain.member.SocialIdentity;
import com.sssok.renewal.domain.member.SocialProvider;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwsHeader;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import java.time.Duration;
import java.time.Instant;
import org.springframework.util.LinkedMultiValueMap;

public class AppleAuthClient implements SocialAuthClient {
    private static final String TOKEN_ENDPOINT = "https://appleid.apple.com/auth/token";
    private static final String TOKEN_ISSUER = "https://appleid.apple.com";
    private static final Duration ALLOWED_ISSUED_AT_CLOCK_SKEW = Duration.ofSeconds(60);
    private static final int MAX_SUBJECT_LENGTH = 255;

    private final OAuthHttpClient oauthHttpClient;
    private final AppleSigningKeyProvider appleSigningKeyProvider;
    private final AppleClientSecretGenerator clientSecretGenerator;
    private final String clientId;
    private final String redirectUri;

    public AppleAuthClient(
        OAuthHttpClient oauthHttpClient,
        AppleSigningKeyProvider appleSigningKeyProvider,
        AppleClientSecretGenerator clientSecretGenerator,
        String clientId,
        String redirectUri
    ) {
        this.oauthHttpClient = oauthHttpClient;
        this.appleSigningKeyProvider = appleSigningKeyProvider;
        this.clientSecretGenerator = clientSecretGenerator;
        this.clientId = clientId;
        this.redirectUri = redirectUri;
    }

    @Override
    public SocialProvider provider() {
        return SocialProvider.APPLE;
    }

    @Override
    public SocialAuthentication authenticate(String authorizationCode, String expectedNonce) {
        validateAuthenticationRequest(authorizationCode, expectedNonce);

        JsonNode tokenResponse = exchangeAuthorizationCode(authorizationCode);
        String idToken = OAuthHttpClient.readRequiredText(tokenResponse, "id_token");
        String providerUserId = verifyIdTokenAndGetSubject(idToken, expectedNonce);

        return new SocialAuthentication(
            new SocialIdentity(provider(), providerUserId),
            OAuthHttpClient.readRequiredText(tokenResponse, "access_token"),
            tokenResponse.path("refresh_token").asText(null)
        );
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
        LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", clientId);
        form.add("client_secret", clientSecretGenerator.generate());
        form.add("token", token);
        form.add("token_type_hint", hasRefreshToken ? "refresh_token" : "access_token");
        oauthHttpClient.postRevocation("https://appleid.apple.com/auth/revoke", form);
    }

    private void validateAuthenticationRequest(String authorizationCode, String expectedNonce) {
        if (authorizationCode == null || authorizationCode.isBlank()
            || expectedNonce == null || expectedNonce.isBlank()) {
            throw SocialAuthException.invalid();
        }
    }

    private JsonNode exchangeAuthorizationCode(String authorizationCode) {
        LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", clientId);
        form.add("client_secret", clientSecretGenerator.generate());
        form.add("redirect_uri", redirectUri);
        form.add("code", authorizationCode);
        return oauthHttpClient.postForm(TOKEN_ENDPOINT, form);
    }

    private String verifyIdTokenAndGetSubject(String idToken, String expectedNonce) {
        try {
            Claims claims = Jwts.parser()
                .keyLocator(header -> {
                    if (!(header instanceof JwsHeader signedHeader)) {
                        throw SocialAuthException.invalid();
                    }
                    return appleSigningKeyProvider.findSigningKey(signedHeader.getKeyId(), signedHeader.getAlgorithm());
                })
                .requireIssuer(TOKEN_ISSUER)
                .requireAudience(clientId)
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
