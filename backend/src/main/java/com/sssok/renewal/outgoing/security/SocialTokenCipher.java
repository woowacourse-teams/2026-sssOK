package com.sssok.renewal.outgoing.security;

import com.sssok.renewal.application.auth.exception.SocialAuthException;
import com.sssok.renewal.domain.member.SocialIdentity;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class SocialTokenCipher {
    public enum TokenType { ACCESS, REFRESH }

    private static final String PREFIX = "v1:";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private final SecureRandom secureRandom = new SecureRandom();
    private final SecretKeySpec encryptionKey;

    public SocialTokenCipher(String encodedKey) {
        if (encodedKey == null || encodedKey.isBlank()) {
            encryptionKey = null;
            return;
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(encodedKey);
            if (decoded.length != 32) throw new IllegalArgumentException();
            encryptionKey = new SecretKeySpec(decoded, "AES");
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("OAuth 토큰 암호화 키는 32바이트의 Base64 값이어야 합니다");
        }
    }

    public void requireConfigured() {
        if (encryptionKey == null) throw new IllegalStateException("oauth.token-encryption-key 설정이 필요합니다");
    }

    public String encrypt(SocialIdentity identity, TokenType type, String token) {
        requireAvailable();
        if (token == null || token.isBlank()) throw SocialAuthException.unavailable();
        byte[] iv = new byte[IV_BYTES];
        secureRandom.nextBytes(iv);
        try {
            Cipher cipher = cipher(Cipher.ENCRYPT_MODE, identity, type, iv);
            byte[] encrypted = cipher.doFinal(token.getBytes(StandardCharsets.UTF_8));
            byte[] payload = ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array();
            return PREFIX + Base64.getEncoder().encodeToString(payload);
        } catch (GeneralSecurityException e) {
            throw SocialAuthException.unavailable();
        }
    }

    public String decrypt(SocialIdentity identity, TokenType type, String stored) {
        requireAvailable();
        try {
            if (stored == null || !stored.startsWith(PREFIX)) throw new IllegalArgumentException();
            byte[] payload = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            if (payload.length <= IV_BYTES + TAG_BITS / 8) throw new IllegalArgumentException();
            ByteBuffer buffer = ByteBuffer.wrap(payload);
            byte[] iv = new byte[IV_BYTES];
            buffer.get(iv);
            byte[] encrypted = new byte[buffer.remaining()];
            buffer.get(encrypted);
            return new String(cipher(Cipher.DECRYPT_MODE, identity, type, iv).doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            // 암호문·키·원인을 로그 또는 외부 응답에 노출하지 않는다.
            throw SocialAuthException.unavailable();
        }
    }

    private Cipher cipher(int mode, SocialIdentity identity, TokenType type, byte[] iv) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, encryptionKey, new GCMParameterSpec(TAG_BITS, iv));
        // 계정 간 암호문 교체와 access/refresh 컬럼 교체도 검증 실패로 처리한다.
        String context = PREFIX + identity.provider().name() + ":" + type.name() + ":" + identity.providerUserId();
        cipher.updateAAD(context.getBytes(StandardCharsets.UTF_8));
        return cipher;
    }

    private void requireAvailable() {
        if (encryptionKey == null) throw SocialAuthException.unavailable();
    }
}
