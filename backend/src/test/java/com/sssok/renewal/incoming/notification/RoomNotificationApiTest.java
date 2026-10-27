package com.sssok.renewal.incoming.notification;

import static com.sssok.support.UploadSizePolicyFixture.SIZE_POLICY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sssok.application.port.out.FileRepository;
import com.sssok.application.room.CreateRoomService;
import com.sssok.application.room.JoinRoomService;
import com.sssok.domain.file.FileSize;
import com.sssok.domain.file.StoredFile;
import com.sssok.domain.room.Room;
import com.sssok.renewal.application.auth.AnonymousAuthService;
import com.sssok.renewal.application.auth.AuthResult;
import com.sssok.renewal.domain.notification.PushSubscription;
import com.sssok.renewal.outgoing.database.PushSubscriptionJpaRepository;
import com.sssok.renewal.outgoing.push.PushSendResult;
import com.sssok.renewal.outgoing.push.WebPushClient;
import com.sssok.renewal.outgoing.storage.FileStorage.UploadedObject;
import com.sssok.renewal.outgoing.storage.FileStorage;
import com.sssok.support.AcceptanceTest;
import com.sssok.support.PostgresContainerSupport;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

// #593 방 참여자 알림. 푸시 서비스로 나가는 HTTP(WebPushClient)만 목으로 두고,
// 구독·설정 저장과 받는 사람 고르기(같은 방·본인 제외·알림 끈 사람 제외)는 실제 DB 로 확인한다.
@AcceptanceTest
@SpringBootTest(properties = {
    "jwt.secret=bG9jYWwtY29udHJhY3QtdGVzdC1rZXktbm90LWZvci1wcm9kdWN0aW9uLTU5Mw==",
    "notification.upload-request.cooldown=1m"
})
class RoomNotificationApiTest extends PostgresContainerSupport {

    private static final Duration ASYNC_WAIT = Duration.ofSeconds(3);
    private static final String P256DH =
        Base64.getUrlEncoder().withoutPadding().encodeToString(uncompressedKey());
    private static final String AUTH = Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[16]);

    @Autowired
    WebApplicationContext context;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    AnonymousAuthService anonymousAuthService;

    @Autowired
    CreateRoomService createRoomService;

    @Autowired
    JoinRoomService joinRoomService;

    @Autowired
    FileRepository fileRepository;

    @Autowired
    PushSubscriptionJpaRepository pushSubscriptionJpaRepository;

    @MockitoBean
    WebPushClient webPushClient;

    @MockitoBean
    FileStorage fileStorage;

    private MockMvc mockMvc;
    private Member host;
    private Member guest;
    private Member otherGuest;
    private Member stranger;
    private Room room;

    private record Member(Long id, String token, String endpoint) {
    }

    private static byte[] uncompressedKey() {
        byte[] key = new byte[65];
        key[0] = 0x04;
        return key;
    }

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        given(webPushClient.enabled()).willReturn(true);
        given(webPushClient.publicKey()).willReturn("BPublicKey");
        given(webPushClient.send(any(), anyString())).willReturn(PushSendResult.SENT);
        given(fileStorage.presignGet(any(), anyString(), anyString(), any(Duration.class)))
            .willReturn("https://storage.test/read");

        host = member("가현");
        room = createRoomService.create(host.id(), "제주 여행", null).room();
        guest = member("민수");
        joinRoomService.join(room.getId(), guest.id());
        otherGuest = member("지수");
        joinRoomService.join(room.getId(), otherGuest.id());

        stranger = member("철수");
        createRoomService.create(stranger.id(), "다른 방", null);

        for (Member member : List.of(host, guest, otherGuest, stranger)) {
            subscribe(member).andExpect(status().isNoContent());
        }
    }

    private Member member(String nickname) {
        AuthResult auth = anonymousAuthService.authenticate(nickname);
        String endpoint = "https://fcm.googleapis.com/fcm/send/" + UUID.randomUUID();
        return new Member(auth.userId(), "Bearer " + auth.accessToken(), endpoint);
    }

    private ResultActions subscribe(Member member) throws Exception {
        return subscribe(member.token(), member.endpoint(), P256DH);
    }

    private ResultActions subscribe(String token, String endpoint, String p256dh) throws Exception {
        String body = """
            {"endpoint":"%s","expirationTime":null,"keys":{"p256dh":"%s","auth":"%s"}}
            """.formatted(endpoint, p256dh, AUTH);
        return mockMvc.perform(post("/api/v1/push-subscriptions")
            .header("Authorization", token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
    }

    private ResultActions updateSetting(Member member, boolean enabled) throws Exception {
        return mockMvc.perform(put("/api/v1/rooms/{roomId}/notification-setting", room.getId())
            .header("Authorization", member.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"enabled\":" + enabled + "}"));
    }

    private ResultActions requestUpload(Member member) throws Exception {
        return mockMvc.perform(post("/api/v1/rooms/{roomId}/upload-requests", room.getId())
            .header("Authorization", member.token()));
    }

    private ResultActions completeUpload(Member member, List<Long> mediaIds) throws Exception {
        return mockMvc.perform(post("/api/v1/rooms/{roomId}/media", room.getId())
            .header("Authorization", member.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(new CompleteUploadBody(mediaIds))));
    }

    private record CompleteUploadBody(List<Long> mediaIds) {
    }

    private Long reservedMedia(Member uploader) {
        return fileRepository.save(StoredFile.reserve(room.getId(), uploader.id(), "a.jpg", "image/jpeg",
            new FileSize(1024), Instant.now(), SIZE_POLICY)).getId();
    }

    private PushSubscription sentTo(Member member) {
        return argThat(subscription -> subscription != null && subscription.getEndpoint().equals(member.endpoint()));
    }

    // 다른 테스트에서 남은 비동기 발송이 섞이지 않게 이 테스트에서 만든 참여자에게 간 것만 센다.
    private void assertNoSendToRoomMembers() {
        verify(webPushClient, after(500).never()).send(sentTo(host), anyString());
        for (Member member : List.of(guest, otherGuest, stranger)) {
            verify(webPushClient, never()).send(sentTo(member), anyString());
        }
    }

    private JsonNode sentPayload(Member member) throws Exception {
        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(webPushClient, timeout(ASYNC_WAIT.toMillis())).send(sentTo(member), payload.capture());
        return objectMapper.readTree(payload.getValue());
    }

    @Nested
    class 알림_설정 {

        @Test
        void 바꾼_적이_없으면_켜져_있다() throws Exception {
            mockMvc.perform(get("/api/v1/rooms/{roomId}/notification-setting", room.getId())
                    .header("Authorization", guest.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(true));
        }

        @Test
        void 끈_값이_저장되어_다시_조회해도_꺼져_있다() throws Exception {
            updateSetting(guest, false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(false));

            mockMvc.perform(get("/api/v1/rooms/{roomId}/notification-setting", room.getId())
                    .header("Authorization", guest.token()))
                .andExpect(jsonPath("$.data.enabled").value(false));
        }

        @Test
        void 다시_켤_수_있다() throws Exception {
            updateSetting(guest, false);

            updateSetting(guest, true).andExpect(jsonPath("$.data.enabled").value(true));
        }

        @Test
        void 설정은_참여자마다_따로_저장된다() throws Exception {
            updateSetting(guest, false);

            mockMvc.perform(get("/api/v1/rooms/{roomId}/notification-setting", room.getId())
                    .header("Authorization", otherGuest.token()))
                .andExpect(jsonPath("$.data.enabled").value(true));
        }

        @Test
        void 입장하지_않은_방의_설정은_바꿀_수_없다() throws Exception {
            updateSetting(stranger, false)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_ROOM_MEMBER"));
        }

        @Test
        void enabled_가_없으면_400() throws Exception {
            mockMvc.perform(put("/api/v1/rooms/{roomId}/notification-setting", room.getId())
                    .header("Authorization", guest.token())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"))
                .andExpect(status().isBadRequest());
        }
    }

    @Nested
    class 업로드_요청 {

        @Test
        void 같은_방의_다른_참여자에게_요청_알림이_간다() throws Exception {
            requestUpload(guest).andExpect(status().isAccepted());

            JsonNode payload = sentPayload(host);
            assertThat(payload.get("type").asText()).isEqualTo("UPLOAD_REQUESTED");
            assertThat(payload.get("roomId").asLong()).isEqualTo(room.getId());
            assertThat(payload.get("roomCode").asText()).isEqualTo(room.getCode().value());
            assertThat(payload.get("url").asText()).isEqualTo("/rooms/" + room.getCode().value() + "/gallery");
            assertThat(payload.get("title").asText()).isEqualTo("민수님이 알림을 보냈습니다");
            assertThat(payload.get("body").asText()).isEqualTo("민수님의 사진을 올려주세요!");
            verify(webPushClient, timeout(ASYNC_WAIT.toMillis())).send(sentTo(otherGuest), anyString());
        }

        @Test
        void 요청한_본인과_다른_방_참여자에게는_가지_않는다() throws Exception {
            requestUpload(guest).andExpect(status().isAccepted());

            verify(webPushClient, timeout(ASYNC_WAIT.toMillis())).send(sentTo(host), anyString());
            verify(webPushClient, after(300).never()).send(sentTo(guest), anyString());
            verify(webPushClient, never()).send(sentTo(stranger), anyString());
        }

        @Test
        void 알림을_끈_참여자에게는_가지_않는다() throws Exception {
            updateSetting(otherGuest, false);

            requestUpload(guest).andExpect(status().isAccepted());

            verify(webPushClient, timeout(ASYNC_WAIT.toMillis())).send(sentTo(host), anyString());
            verify(webPushClient, after(300).never()).send(sentTo(otherGuest), anyString());
        }

        @Test
        void 쿨다운_안에_다시_요청하면_429() throws Exception {
            requestUpload(guest).andExpect(status().isAccepted());

            requestUpload(guest)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("UPLOAD_REQUEST_COOLDOWN"));
        }

        @Test
        void 쿨다운은_요청한_사람마다_따로_센다() throws Exception {
            requestUpload(guest).andExpect(status().isAccepted());

            requestUpload(otherGuest).andExpect(status().isAccepted());
        }

        @Test
        void 입장하지_않은_방에는_요청할_수_없다() throws Exception {
            requestUpload(stranger).andExpect(status().isForbidden());
        }

        @Test
        void 만료된_구독은_푸시_서비스가_알려_오면_지운다() throws Exception {
            given(webPushClient.send(sentTo(host), anyString())).willReturn(PushSendResult.EXPIRED);

            requestUpload(guest).andExpect(status().isAccepted());

            await().atMost(ASYNC_WAIT).untilAsserted(() -> assertThat(pushSubscriptionJpaRepository.findAll())
                .extracting(subscription -> subscription.getEndpoint())
                .doesNotContain(host.endpoint())
                .contains(otherGuest.endpoint()));
        }

        @Test
        void 한_사람에게_발송이_실패해도_나머지_참여자에게는_보낸다() throws Exception {
            given(webPushClient.send(sentTo(host), anyString())).willThrow(new IllegalStateException("push down"));

            requestUpload(guest).andExpect(status().isAccepted());

            verify(webPushClient, timeout(ASYNC_WAIT.toMillis())).send(sentTo(host), anyString());
            verify(webPushClient, timeout(ASYNC_WAIT.toMillis())).send(sentTo(otherGuest), anyString());
        }

        @Test
        void VAPID_키가_없으면_보내지_않지만_요청은_접수한다() throws Exception {
            given(webPushClient.enabled()).willReturn(false);

            requestUpload(guest).andExpect(status().isAccepted());

            assertNoSendToRoomMembers();
        }
    }

    @Nested
    class 업로드_알림 {

        @Test
        void 여러_장을_한_번에_올려도_참여자마다_한_번만_간다() throws Exception {
            given(fileStorage.findUploaded(any())).willReturn(Optional.of(new UploadedObject(1024, "image/jpeg")));
            List<Long> mediaIds = List.of(reservedMedia(guest), reservedMedia(guest), reservedMedia(guest));

            completeUpload(guest, mediaIds).andExpect(status().isCreated());

            JsonNode payload = sentPayload(host);
            assertThat(payload.get("type").asText()).isEqualTo("MEDIA_UPLOADED");
            assertThat(payload.get("body").asText()).isEqualTo("민수님이 사진 3장을 올렸어요");
            assertThat(payload.get("roomId").asLong()).isEqualTo(room.getId());
            verify(webPushClient, after(300).times(1)).send(sentTo(host), anyString());
            verify(webPushClient, times(1)).send(sentTo(otherGuest), anyString());
        }

        @Test
        void 올린_본인과_알림을_끈_참여자와_다른_방_참여자에게는_가지_않는다() throws Exception {
            given(fileStorage.findUploaded(any())).willReturn(Optional.of(new UploadedObject(1024, "image/jpeg")));
            updateSetting(otherGuest, false);

            completeUpload(guest, List.of(reservedMedia(guest))).andExpect(status().isCreated());

            verify(webPushClient, timeout(ASYNC_WAIT.toMillis())).send(sentTo(host), anyString());
            verify(webPushClient, after(300).never()).send(sentTo(guest), anyString());
            verify(webPushClient, never()).send(sentTo(otherGuest), anyString());
            verify(webPushClient, never()).send(sentTo(stranger), anyString());
        }

        @Test
        void 새로_등록된_것이_없으면_알리지_않는다() throws Exception {
            given(fileStorage.findUploaded(any())).willReturn(Optional.empty());

            completeUpload(guest, List.of(reservedMedia(guest))).andExpect(status().isCreated());

            assertNoSendToRoomMembers();
        }

        @Test
        void 알림_발송이_실패해도_업로드는_성공한다() throws Exception {
            given(fileStorage.findUploaded(any())).willReturn(Optional.of(new UploadedObject(1024, "image/jpeg")));
            given(webPushClient.send(any(), anyString())).willThrow(new IllegalStateException("push down"));

            completeUpload(guest, List.of(reservedMedia(guest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.registered.length()").value(1));
        }
    }

    @Nested
    class 구독 {

        @Test
        void 공개키를_받는다() throws Exception {
            mockMvc.perform(get("/api/v1/push-subscriptions/public-key").header("Authorization", guest.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.publicKey").value("BPublicKey"));
        }

        @Test
        void VAPID_키가_없으면_공개키_조회는_503() throws Exception {
            given(webPushClient.enabled()).willReturn(false);

            mockMvc.perform(get("/api/v1/push-subscriptions/public-key").header("Authorization", guest.token()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("WEB_PUSH_UNAVAILABLE"));
        }

        @Test
        void 같은_endpoint로_다시_등록하면_마지막_계정으로_옮겨진다() throws Exception {
            subscribe(otherGuest.token(), guest.endpoint(), P256DH).andExpect(status().isNoContent());

            assertThat(pushSubscriptionJpaRepository.findAll())
                .filteredOn(subscription -> subscription.getEndpoint().equals(guest.endpoint()))
                .singleElement()
                .satisfies(subscription -> assertThat(subscription.getMemberId()).isEqualTo(otherGuest.id()));
        }

        @Test
        void 푸시_서비스가_아닌_주소는_400() throws Exception {
            subscribe(guest.token(), "https://evil.example.com/hook", P256DH)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PUSH_SUBSCRIPTION"));
        }

        @Test
        void 키_형식이_틀리면_400() throws Exception {
            subscribe(guest.token(), "https://fcm.googleapis.com/fcm/send/x", "short")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PUSH_SUBSCRIPTION"));
        }

        @Test
        void 해제하면_더는_알림이_가지_않는다() throws Exception {
            mockMvc.perform(delete("/api/v1/push-subscriptions")
                    .header("Authorization", host.token())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"endpoint\":\"" + host.endpoint() + "\"}"))
                .andExpect(status().isNoContent());

            requestUpload(guest).andExpect(status().isAccepted());

            verify(webPushClient, timeout(ASYNC_WAIT.toMillis())).send(sentTo(otherGuest), anyString());
            verify(webPushClient, after(300).never()).send(sentTo(host), anyString());
        }

        @Test
        void 남의_구독은_해제되지_않는다() throws Exception {
            mockMvc.perform(delete("/api/v1/push-subscriptions")
                    .header("Authorization", guest.token())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"endpoint\":\"" + host.endpoint() + "\"}"))
                .andExpect(status().isNoContent());

            assertThat(pushSubscriptionJpaRepository.findAll())
                .extracting(subscription -> subscription.getEndpoint())
                .contains(host.endpoint());
        }

        @Test
        void 인증_없이는_등록할_수_없다() throws Exception {
            mockMvc.perform(post("/api/v1/push-subscriptions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"))
                .andExpect(status().isUnauthorized());
        }
    }
}
