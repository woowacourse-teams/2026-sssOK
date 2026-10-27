package com.sssok.renewal.incoming.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sssok.application.auth.AnonymousAuthService;
import com.sssok.application.auth.AuthResult;
import com.sssok.application.room.CreateRoomService;
import com.sssok.application.room.JoinRoomService;
import com.sssok.renewal.outgoing.database.PushSubscriptionJpaEntity;
import com.sssok.renewal.outgoing.database.PushSubscriptionJpaRepository;
import com.sssok.renewal.outgoing.push.VapidKeys;
import com.sssok.support.AcceptanceTest;
import com.sssok.support.PostgresContainerSupport;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

// 실제 WebPushClient 로 보내는 경로에서, 쓸 수 없는 구독 공개키를 가진 구독이 지워지는지 본다.
// 공개키 단계에서 실패하므로 푸시 서비스로 HTTP 요청은 나가지 않는다.
@AcceptanceTest
@SpringBootTest(properties = "jwt.secret=bG9jYWwtY29udHJhY3QtdGVzdC1rZXktbm90LWZvci1wcm9kdWN0aW9uLTU5Mw==")
class InvalidSubscriptionKeyApiTest extends PostgresContainerSupport {

    private static final Base64.Encoder BASE64_URL = Base64.getUrlEncoder().withoutPadding();

    @Autowired
    WebApplicationContext context;

    @Autowired
    AnonymousAuthService anonymousAuthService;

    @Autowired
    CreateRoomService createRoomService;

    @Autowired
    JoinRoomService joinRoomService;

    @Autowired
    PushSubscriptionJpaRepository pushSubscriptionJpaRepository;

    @DynamicPropertySource
    static void vapidKeys(DynamicPropertyRegistry registry) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(VapidKeys.p256Spec());
        KeyPair keys = generator.generateKeyPair();
        byte[] scalar = ((ECPrivateKey) keys.getPrivate()).getS().toByteArray();
        byte[] privateKey = new byte[32];
        int start = Math.max(0, scalar.length - 32);
        System.arraycopy(scalar, start, privateKey, 32 - (scalar.length - start), scalar.length - start);

        registry.add("notification.web-push.vapid-public-key",
            () -> BASE64_URL.encodeToString(VapidKeys.uncompressed((ECPublicKey) keys.getPublic())));
        registry.add("notification.web-push.vapid-private-key", () -> BASE64_URL.encodeToString(privateKey));
        registry.add("notification.web-push.subject", () -> "mailto:test@sssok.test");
    }

    @Test
    void 곡선_위에_없는_공개키를_가진_구독은_발송할_때_지운다() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        AuthResult host = anonymousAuthService.authenticate("가현");
        Long roomId = createRoomService.create(host.userId(), "제주 여행", null).room().getId();
        AuthResult guest = anonymousAuthService.authenticate("민수");
        joinRoomService.join(roomId, guest.userId());

        byte[] offCurveKey = new byte[65];
        offCurveKey[0] = 0x04;
        String endpoint = "https://fcm.googleapis.com/fcm/send/" + UUID.randomUUID();
        mockMvc.perform(post("/api/v1/push-subscriptions")
                .header("Authorization", "Bearer " + host.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"endpoint":"%s","keys":{"p256dh":"%s","auth":"%s"}}
                    """.formatted(endpoint, BASE64_URL.encodeToString(offCurveKey),
                    BASE64_URL.encodeToString(new byte[16]))))
            .andExpect(status().isNoContent());
        assertThat(pushSubscriptionJpaRepository.findAll())
            .extracting(PushSubscriptionJpaEntity::getEndpoint).contains(endpoint);

        mockMvc.perform(post("/api/v1/rooms/{roomId}/upload-requests", roomId)
                .header("Authorization", "Bearer " + guest.accessToken()))
            .andExpect(status().isAccepted());

        await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> assertThat(pushSubscriptionJpaRepository.findAll())
            .extracting(PushSubscriptionJpaEntity::getEndpoint).doesNotContain(endpoint));
    }
}
