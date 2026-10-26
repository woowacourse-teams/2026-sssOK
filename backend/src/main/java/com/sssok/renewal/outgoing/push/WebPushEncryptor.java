package com.sssok.renewal.outgoing.push;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.interfaces.ECPublicKey;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

// RFC 8291(Web Push 메시지 암호화) + RFC 8188(aes128gcm). 구독한 브라우저만 풀 수 있게 암호화한다.
// 알림 본문은 짧아 레코드 하나로 충분하다.
public class WebPushEncryptor {
    private static final int RECORD_SIZE = 4096;
    private static final int SALT_LENGTH = 16;
    private static final int TAG_LENGTH_BITS = 128;
    // 레코드 크기에서 패딩 구분자(1)와 GCM 태그(16)를 뺀 값
    static final int MAX_PLAINTEXT_LENGTH = RECORD_SIZE - 1 - 16;
    private static final byte[] KEY_INFO_PREFIX = "WebPush: info\0".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] CEK_INFO = "Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] NONCE_INFO = "Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII);

    private final SecureRandom random = new SecureRandom();

    public byte[] encrypt(byte[] plaintext, byte[] userAgentPublicKey, byte[] authSecret) {
        if (plaintext.length > MAX_PLAINTEXT_LENGTH) {
            throw new IllegalArgumentException("푸시 본문이 너무 깁니다: " + plaintext.length);
        }
        try {
            KeyPair serverKeys = ephemeralKeyPair();
            byte[] serverPublicKey = VapidKeys.uncompressed((ECPublicKey) serverKeys.getPublic());
            byte[] salt = new byte[SALT_LENGTH];
            random.nextBytes(salt);

            byte[] ecdhSecret = ecdhSecret(serverKeys, userAgentPublicKey);

            byte[] keyInfo = concat(KEY_INFO_PREFIX, userAgentPublicKey, serverPublicKey);
            byte[] ikm = hkdf(authSecret, ecdhSecret, keyInfo, 32);
            byte[] contentKey = hkdf(salt, ikm, CEK_INFO, 16);
            byte[] nonce = hkdf(salt, ikm, NONCE_INFO, 12);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(contentKey, "AES"),
                new GCMParameterSpec(TAG_LENGTH_BITS, nonce));
            // 0x02 = 마지막 레코드 구분자. 패딩은 넣지 않는다.
            byte[] ciphertext = cipher.doFinal(concat(plaintext, new byte[] {0x02}));

            return ByteBuffer.allocate(SALT_LENGTH + 4 + 1 + serverPublicKey.length + ciphertext.length)
                .put(salt)
                .putInt(RECORD_SIZE)
                .put((byte) serverPublicKey.length)
                .put(serverPublicKey)
                .put(ciphertext)
                .array();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("푸시 본문 암호화에 실패했습니다", e);
        }
    }

    // 구독 공개키 문제만 InvalidSubscriptionKeyException 으로 구분한다. 그 밖의 암호화 오류는 구독 탓이 아니다.
    private static byte[] ecdhSecret(KeyPair serverKeys, byte[] userAgentPublicKey) throws GeneralSecurityException {
        KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
        agreement.init(serverKeys.getPrivate());
        try {
            PublicKey userAgentKey = VapidKeys.publicKey(userAgentPublicKey);
            agreement.doPhase(userAgentKey, true);
        } catch (IllegalArgumentException | InvalidKeyException e) {
            throw new InvalidSubscriptionKeyException(e);
        }
        return agreement.generateSecret();
    }

    private KeyPair ephemeralKeyPair() throws GeneralSecurityException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(VapidKeys.p256Spec(), random);
        return generator.generateKeyPair();
    }

    // 출력 길이가 32바이트 이하라 HKDF-Expand 는 한 블록(T(1))이면 된다.
    static byte[] hkdf(byte[] salt, byte[] ikm, byte[] info, int length) throws GeneralSecurityException {
        byte[] prk = hmac(salt, ikm);
        return Arrays.copyOf(hmac(prk, concat(info, new byte[] {0x01})), length);
    }

    private static byte[] hmac(byte[] key, byte[] data) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data);
    }

    private static byte[] concat(byte[]... parts) {
        int length = 0;
        for (byte[] part : parts) {
            length += part.length;
        }
        ByteBuffer buffer = ByteBuffer.allocate(length);
        for (byte[] part : parts) {
            buffer.put(part);
        }
        return buffer.array();
    }
}
