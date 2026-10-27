package com.sssok.renewal.outgoing.push;

import java.math.BigInteger;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.ECPublicKeySpec;
import java.util.Arrays;
import java.util.Base64;

// Web Push 에서 쓰는 P-256 키 변환. 브라우저·web-push 도구가 주고받는 형식은
// 공개키 = 비압축 점(0x04 + X + Y, 65바이트), 개인키 = 스칼라 d(32바이트)의 base64url 이다.
public final class VapidKeys {
    private static final int COORDINATE_LENGTH = 32;
    private static final ECParameterSpec P256 = p256();

    private VapidKeys() {
    }

    public static ECParameterSpec p256Spec() {
        return P256;
    }

    public static ECPublicKey publicKey(String base64Url) {
        return publicKey(Base64.getUrlDecoder().decode(base64Url));
    }

    public static ECPublicKey publicKey(byte[] uncompressed) {
        if (uncompressed.length != 1 + COORDINATE_LENGTH * 2 || uncompressed[0] != 0x04) {
            throw new IllegalArgumentException("P-256 비압축 공개키가 아닙니다");
        }
        BigInteger x = new BigInteger(1, Arrays.copyOfRange(uncompressed, 1, 1 + COORDINATE_LENGTH));
        BigInteger y = new BigInteger(1, Arrays.copyOfRange(uncompressed, 1 + COORDINATE_LENGTH, uncompressed.length));
        try {
            return (ECPublicKey) KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(new ECPoint(x, y), P256));
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("P-256 공개키를 만들 수 없습니다", e);
        }
    }

    public static ECPrivateKey privateKey(String base64Url) {
        BigInteger d = new BigInteger(1, Base64.getUrlDecoder().decode(base64Url));
        try {
            return (ECPrivateKey) KeyFactory.getInstance("EC").generatePrivate(new ECPrivateKeySpec(d, P256));
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("P-256 개인키를 만들 수 없습니다", e);
        }
    }

    public static byte[] uncompressed(ECPublicKey key) {
        byte[] result = new byte[1 + COORDINATE_LENGTH * 2];
        result[0] = 0x04;
        copyUnsigned(key.getW().getAffineX(), result, 1);
        copyUnsigned(key.getW().getAffineY(), result, 1 + COORDINATE_LENGTH);
        return result;
    }

    // BigInteger.toByteArray() 는 부호 바이트가 붙거나 앞자리 0 이 빠질 수 있어 32바이트로 맞춘다.
    private static void copyUnsigned(BigInteger value, byte[] target, int offset) {
        byte[] bytes = value.toByteArray();
        int start = Math.max(0, bytes.length - COORDINATE_LENGTH);
        int length = bytes.length - start;
        System.arraycopy(bytes, start, target, offset + COORDINATE_LENGTH - length, length);
    }

    private static ECParameterSpec p256() {
        try {
            AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
            parameters.init(new ECGenParameterSpec("secp256r1"));
            return parameters.getParameterSpec(ECParameterSpec.class);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("P-256 곡선을 지원하지 않는 JVM 입니다", e);
        }
    }
}
