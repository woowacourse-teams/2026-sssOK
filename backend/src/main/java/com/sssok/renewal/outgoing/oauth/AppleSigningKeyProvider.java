package com.sssok.renewal.outgoing.oauth;

import com.fasterxml.jackson.databind.JsonNode;
import com.sssok.renewal.application.auth.exception.SocialAuthException;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

public class AppleSigningKeyProvider {
    private static final String SIGNING_KEYS_ENDPOINT = "https://appleid.apple.com/auth/keys";
    private static final String SIGNATURE_ALGORITHM = "RS256";
    private static final Duration CACHE_TTL = Duration.ofHours(1);
    private static final Duration MIN_REFRESH_INTERVAL = Duration.ofMinutes(1);
    private static final int MAX_KEY_COUNT = 32;
    private static final int MIN_RSA_KEY_BITS = 2048;

    private final OAuthHttpClient oauthHttpClient;
    private Map<String, PublicKey> cachedKeys = Map.of();
    private Instant lastFetchedAt = Instant.EPOCH;

    public AppleSigningKeyProvider(OAuthHttpClient oauthHttpClient) {
        this.oauthHttpClient = oauthHttpClient;
    }

    public synchronized PublicKey findSigningKey(String keyId, String algorithm) {
        if (keyId == null || keyId.isBlank() || !SIGNATURE_ALGORITHM.equals(algorithm)) {
            throw SocialAuthException.invalid();
        }
        Instant now = Instant.now();
        if (shouldRefreshKeys(keyId, now)) {
            refreshKeys(now);
        }
        PublicKey signingKey = cachedKeys.get(keyId);
        if (signingKey == null) {
            throw SocialAuthException.invalid();
        }
        return signingKey;
    }

    private boolean shouldRefreshKeys(String keyId, Instant now) {
        boolean cacheExpired = !now.isBefore(lastFetchedAt.plus(CACHE_TTL));
        boolean unknownKey = !cachedKeys.containsKey(keyId);
        boolean refreshIntervalElapsed = !now.isBefore(lastFetchedAt.plus(MIN_REFRESH_INTERVAL));
        // 알 수 없는 키 ID가 반복 입력되어도 외부 호출을 무한히 발생시키지 않는다.
        return cacheExpired || (unknownKey && refreshIntervalElapsed);
    }

    private void refreshKeys(Instant now) {
        JsonNode keyEntries = oauthHttpClient.getJson(SIGNING_KEYS_ENDPOINT, null).path("keys");
        if (!keyEntries.isArray() || keyEntries.size() > MAX_KEY_COUNT) {
            throw SocialAuthException.unavailable();
        }
        Map<String, PublicKey> loadedKeys = new HashMap<>();
        try {
            for (JsonNode keyEntry : keyEntries) {
                if (!isSupportedSigningKey(keyEntry)) {
                    continue;
                }
                String keyId = OAuthHttpClient.readRequiredText(keyEntry, "kid");
                if (loadedKeys.containsKey(keyId)) {
                    throw SocialAuthException.unavailable();
                }
                loadedKeys.put(keyId, parseRsaPublicKey(keyEntry));
            }
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw SocialAuthException.unavailable();
        }
        if (loadedKeys.isEmpty()) {
            throw SocialAuthException.unavailable();
        }
        cachedKeys = Map.copyOf(loadedKeys);
        lastFetchedAt = now;
    }

    private boolean isSupportedSigningKey(JsonNode keyEntry) {
        return "RSA".equals(keyEntry.path("kty").asText())
            && SIGNATURE_ALGORITHM.equals(keyEntry.path("alg").asText())
            && "sig".equals(keyEntry.path("use").asText());
    }

    private PublicKey parseRsaPublicKey(JsonNode keyEntry) throws GeneralSecurityException {
        BigInteger modulus = decodeUnsignedInteger(OAuthHttpClient.readRequiredText(keyEntry, "n"));
        BigInteger exponent = decodeUnsignedInteger(OAuthHttpClient.readRequiredText(keyEntry, "e"));
        if (modulus.bitLength() < MIN_RSA_KEY_BITS) {
            throw SocialAuthException.unavailable();
        }
        return KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(modulus, exponent));
    }

    private BigInteger decodeUnsignedInteger(String encodedValue) {
        return new BigInteger(1, Base64.getUrlDecoder().decode(encodedValue));
    }
}
