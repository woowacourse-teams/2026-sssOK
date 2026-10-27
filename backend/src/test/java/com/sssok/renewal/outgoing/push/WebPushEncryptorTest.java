package com.sssok.renewal.outgoing.push;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.interfaces.ECPublicKey;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

// 받는 브라우저 입장에서 RFC 8291 을 따로 구현해 풀어 본다. 암호화 쪽 코드를 재사용하지 않아야
// 양쪽이 같은 실수를 해서 통과하는 일을 막을 수 있다.
class WebPushEncryptorTest {

    private final WebPushEncryptor encryptor = new WebPushEncryptor();
    private KeyPair browserKeys;
    private byte[] browserPublicKey;
    private byte[] authSecret;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(VapidKeys.p256Spec());
        browserKeys = generator.generateKeyPair();
        browserPublicKey = VapidKeys.uncompressed((ECPublicKey) browserKeys.getPublic());
        authSecret = new byte[16];
        new SecureRandom().nextBytes(authSecret);
    }

    @Test
    void 구독한_브라우저의_개인키로_풀면_원문이_나온다() throws Exception {
        String payload = "{\"title\":\"제주 여행\",\"body\":\"민수님이 사진 3장을 올렸어요\"}";

        byte[] encrypted = encryptor.encrypt(payload.getBytes(StandardCharsets.UTF_8), browserPublicKey, authSecret);

        assertThat(new String(decryptAsBrowser(encrypted), StandardCharsets.UTF_8)).isEqualTo(payload);
    }

    @Test
    void 헤더에_레코드_크기와_서버_임시_공개키를_싣는다() {
        byte[] encrypted = encryptor.encrypt("hi".getBytes(StandardCharsets.UTF_8), browserPublicKey, authSecret);

        ByteBuffer header = ByteBuffer.wrap(encrypted);
        header.position(16);
        assertThat(header.getInt()).isEqualTo(4096);
        assertThat(header.get()).isEqualTo((byte) 65);
        assertThat(header.get()).isEqualTo((byte) 0x04);
    }

    @Test
    void 같은_내용도_보낼_때마다_다르게_암호화된다() {
        byte[] payload = "hi".getBytes(StandardCharsets.UTF_8);

        assertThat(encryptor.encrypt(payload, browserPublicKey, authSecret))
            .isNotEqualTo(encryptor.encrypt(payload, browserPublicKey, authSecret));
    }

    @Test
    void 곡선_위에_없는_구독_공개키는_구독_키_오류로_구분한다() {
        byte[] offCurveKey = new byte[65];
        offCurveKey[0] = 0x04;

        assertThatThrownBy(() -> encryptor.encrypt("hi".getBytes(StandardCharsets.UTF_8), offCurveKey, authSecret))
            .isInstanceOf(InvalidSubscriptionKeyException.class);
    }

    @Test
    void 레코드_하나에_담기지_않는_본문은_거절한다() {
        byte[] tooLong = new byte[WebPushEncryptor.MAX_PLAINTEXT_LENGTH + 1];

        // 구독 탓이 아니므로 구독 키 오류로 분류하지 않는다.
        assertThatThrownBy(() -> encryptor.encrypt(tooLong, browserPublicKey, authSecret))
            .isInstanceOf(IllegalArgumentException.class)
            .isNotInstanceOf(InvalidSubscriptionKeyException.class);
    }

    private byte[] decryptAsBrowser(byte[] message) throws Exception {
        ByteBuffer buffer = ByteBuffer.wrap(message);
        byte[] salt = new byte[16];
        buffer.get(salt);
        buffer.getInt();
        byte[] serverPublicKey = new byte[buffer.get()];
        buffer.get(serverPublicKey);
        byte[] ciphertext = new byte[buffer.remaining()];
        buffer.get(ciphertext);

        KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
        agreement.init(browserKeys.getPrivate());
        agreement.doPhase(VapidKeys.publicKey(serverPublicKey), true);
        byte[] ecdhSecret = agreement.generateSecret();

        byte[] prkKey = hmac(authSecret, ecdhSecret);
        byte[] ikm = hmac(prkKey, concat("WebPush: info\0".getBytes(StandardCharsets.US_ASCII),
            browserPublicKey, serverPublicKey, new byte[] {1}));
        byte[] prk = hmac(salt, ikm);
        byte[] cek = Arrays.copyOf(hmac(prk, "Content-Encoding: aes128gcm\0\1".getBytes(StandardCharsets.US_ASCII)), 16);
        byte[] nonce = Arrays.copyOf(hmac(prk, "Content-Encoding: nonce\0\1".getBytes(StandardCharsets.US_ASCII)), 12);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
        byte[] padded = cipher.doFinal(ciphertext);
        assertThat(padded[padded.length - 1]).isEqualTo((byte) 0x02);
        return Arrays.copyOf(padded, padded.length - 1);
    }

    private static byte[] hmac(byte[] key, byte[] data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data);
    }

    private static byte[] concat(byte[]... parts) {
        ByteBuffer buffer = ByteBuffer.allocate(Arrays.stream(parts).mapToInt(part -> part.length).sum());
        Arrays.stream(parts).forEach(buffer::put);
        return buffer.array();
    }
}
