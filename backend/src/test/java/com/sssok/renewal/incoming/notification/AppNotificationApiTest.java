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
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sssok.renewal.application.auth.AnonymousAuthService;
import com.sssok.renewal.application.auth.AuthResult;
import com.sssok.application.port.out.FileRepository;
import com.sssok.application.room.CreateRoomService;
import com.sssok.application.room.RoomMembershipService;
import com.sssok.domain.file.FileSize;
import com.sssok.domain.file.StoredFile;
import com.sssok.domain.room.Room;
import com.sssok.renewal.domain.notification.DevicePlatform;
import com.sssok.renewal.domain.notification.DeviceToken;
import com.sssok.renewal.domain.notification.PushSubscription;
import com.sssok.renewal.domain.notification.RoomNotification;
import com.sssok.renewal.domain.notification.RoomNotificationType;
import com.sssok.renewal.outgoing.database.DeviceTokenJpaEntity;
import com.sssok.renewal.outgoing.database.DeviceTokenJpaRepository;
import com.sssok.renewal.outgoing.push.FcmClient;
import com.sssok.renewal.outgoing.push.PushSendResult;
import com.sssok.renewal.outgoing.push.WebPushClient;
import com.sssok.renewal.outgoing.storage.FileStorage;
import com.sssok.renewal.outgoing.storage.FileStorage.UploadedObject;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

// #601 네이티브 앱 알림. FCM·Web Push 로 나가는 HTTP 만 목으로 두고,
// 기기 토큰 저장과 받는 사람 고르기(같은 방·본인 제외·알림 끈 사람 제외)는 실제 DB 로 확인한다.
@AcceptanceTest
@SpringBootTest(properties = {
    "jwt.secret=bG9jYWwtY29udHJhY3QtdGVzdC1rZXktbm90LWZvci1wcm9kdWN0aW9uLTYwMQ==",
    "notification.upload-request.cooldown=1m"
})
class AppNotificationApiTest extends PostgresContainerSupport {

    private static final Duration ASYNC_WAIT = Duration.ofSeconds(3);

    @Autowired
    WebApplicationContext context;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    AnonymousAuthService anonymousAuthService;

    @Autowired
    CreateRoomService createRoomService;

    @Autowired
    RoomMembershipService roomMembershipService;

    @Autowired
    FileRepository fileRepository;

    @Autowired
    DeviceTokenJpaRepository deviceTokenJpaRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @MockitoBean
    FcmClient fcmClient;

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

    private record Member(Long id, String authorization, String deviceToken) {
    }

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        given(fcmClient.enabled()).willReturn(true);
        given(fcmClient.send(any(), any())).willReturn(PushSendResult.SENT);
        given(webPushClient.enabled()).willReturn(true);
        given(webPushClient.send(any(), anyString())).willReturn(PushSendResult.SENT);
        given(fileStorage.presignGet(any(), anyString(), anyString(), any(Duration.class)))
            .willReturn("https://storage.test/read");

        host = member("가현");
        room = createRoomService.create(host.id(), "제주 여행", null).room();
        guest = member("민수");
        roomMembershipService.join(room.getId(), room.getCode(), guest.id());
        otherGuest = member("지수");
        roomMembershipService.join(room.getId(), room.getCode(), otherGuest.id());

        stranger = member("철수");
        createRoomService.create(stranger.id(), "다른 방", null);

        for (Member member : List.of(host, guest, otherGuest, stranger)) {
            registerToken(member.authorization(), member.deviceToken(), "ios").andExpect(status().isNoContent());
        }
    }

    private Member member(String nickname) {
        AuthResult auth = anonymousAuthService.authenticate(nickname);
        String deviceToken = "fcm-" + UUID.randomUUID() + ":APA91b" + UUID.randomUUID().toString().replace("-", "");
        return new Member(auth.userId(), "Bearer " + auth.accessToken(), deviceToken);
    }

    private ResultActions registerToken(String authorization, String token, String platform) throws Exception {
        return mockMvc.perform(post("/api/v1/device-tokens")
            .header("Authorization", authorization)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"token\":\"%s\",\"platform\":\"%s\"}".formatted(token, platform)));
    }

    private ResultActions unregisterToken(Member member, String token) throws Exception {
        return mockMvc.perform(delete("/api/v1/device-tokens")
            .header("Authorization", member.authorization())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"token\":\"" + token + "\"}"));
    }

    private void subscribeWebPush(Member member, String endpoint) throws Exception {
        String key = Base64.getUrlEncoder().withoutPadding().encodeToString(uncompressedKey());
        String auth = Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[16]);
        mockMvc.perform(post("/api/v1/push-subscriptions")
                .header("Authorization", member.authorization())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"endpoint\":\"%s\",\"keys\":{\"p256dh\":\"%s\",\"auth\":\"%s\"}}"
                    .formatted(endpoint, key, auth)))
            .andExpect(status().isNoContent());
    }

    private static byte[] uncompressedKey() {
        byte[] key = new byte[65];
        key[0] = 0x04;
        return key;
    }

    private void disableNotification(Member member) throws Exception {
        mockMvc.perform(put("/api/v1/rooms/{roomId}/notification-setting", room.getId())
                .header("Authorization", member.authorization())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":false}"))
            .andExpect(status().isOk());
    }

    private ResultActions requestUpload(Member member) throws Exception {
        return mockMvc.perform(post("/api/v1/rooms/{roomId}/upload-requests", room.getId())
            .header("Authorization", member.authorization()));
    }

    private ResultActions completeUpload(Member member, List<Long> mediaIds) throws Exception {
        return mockMvc.perform(post("/api/v1/rooms/{roomId}/media", room.getId())
            .header("Authorization", member.authorization())
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(new CompleteUploadBody(mediaIds))));
    }

    private record CompleteUploadBody(List<Long> mediaIds) {
    }

    private Long reservedMedia(Member uploader) {
        return fileRepository.save(StoredFile.reserve(room.getId(), uploader.id(), "a.jpg", "image/jpeg",
            new FileSize(1024), Instant.now(), SIZE_POLICY)).getId();
    }

    private DeviceToken sentTo(Member member) {
        return argThat(token -> token != null && token.getToken().equals(member.deviceToken()));
    }

    private RoomNotification sentNotification(Member member) {
        ArgumentCaptor<RoomNotification> notification = ArgumentCaptor.forClass(RoomNotification.class);
        verify(fcmClient, timeout(ASYNC_WAIT.toMillis())).send(sentTo(member), notification.capture());
        return notification.getValue();
    }

    private List<String> savedTokens() {
        return deviceTokenJpaRepository.findAll().stream().map(DeviceTokenJpaEntity::getToken).toList();
    }

    @Nested
    class 업로드_요청 {

        @Test
        void 같은_방의_다른_참여자_기기로_방_이동_정보와_함께_간다() throws Exception {
            requestUpload(guest).andExpect(status().isAccepted());

            RoomNotification notification = sentNotification(host);
            assertThat(notification.type()).isEqualTo(RoomNotificationType.UPLOAD_REQUESTED);
            assertThat(notification.roomId()).isEqualTo(room.getId());
            assertThat(notification.roomCode()).isEqualTo(room.getCode().value());
            assertThat(notification.url()).isEqualTo("/rooms/" + room.getCode().value() + "/gallery");
            verify(fcmClient, timeout(ASYNC_WAIT.toMillis())).send(sentTo(otherGuest), any());
        }

        @Test
        void 요청한_본인과_알림을_끈_참여자와_다른_방_참여자에게는_가지_않는다() throws Exception {
            disableNotification(otherGuest);

            requestUpload(guest).andExpect(status().isAccepted());

            verify(fcmClient, timeout(ASYNC_WAIT.toMillis())).send(sentTo(host), any());
            verify(fcmClient, after(300).never()).send(sentTo(guest), any());
            verify(fcmClient, never()).send(sentTo(otherGuest), any());
            verify(fcmClient, never()).send(sentTo(stranger), any());
        }

        @Test
        void 남의_방에_참여했던_회원이_탈퇴하면_앱과_웹_모두로_가지_않는다() throws Exception {
            String endpoint = "https://fcm.googleapis.com/fcm/send/" + UUID.randomUUID();
            subscribeWebPush(otherGuest, endpoint);
            // 탈퇴해도 남이 만든 방의 참여 기록과 기기 토큰·구독은 남는다.
            jdbcTemplate.update("UPDATE member SET status = 'WITHDRAWN' WHERE id = ?", otherGuest.id());

            requestUpload(guest).andExpect(status().isAccepted());

            verify(fcmClient, timeout(ASYNC_WAIT.toMillis())).send(sentTo(host), any());
            verify(fcmClient, after(300).never()).send(sentTo(otherGuest), any());
            verify(webPushClient, never()).send(any(), anyString());
        }

        @Test
        void 웹과_앱을_함께_쓰면_둘_다로_간다() throws Exception {
            String endpoint = "https://fcm.googleapis.com/fcm/send/" + UUID.randomUUID();
            subscribeWebPush(host, endpoint);

            requestUpload(guest).andExpect(status().isAccepted());

            verify(fcmClient, timeout(ASYNC_WAIT.toMillis())).send(sentTo(host), any());
            verify(webPushClient, timeout(ASYNC_WAIT.toMillis())).send(
                argThat((PushSubscription subscription) -> subscription != null
                    && subscription.getEndpoint().equals(endpoint)), anyString());
        }

        @Test
        void 한_기기에_여러_계정을_번갈아_쓰면_마지막_계정으로만_간다() throws Exception {
            registerToken(guest.authorization(), host.deviceToken(), "ios").andExpect(status().isNoContent());

            requestUpload(otherGuest).andExpect(status().isAccepted());

            verify(fcmClient, timeout(ASYNC_WAIT.toMillis())).send(
                argThat((DeviceToken token) -> token != null && token.getToken().equals(host.deviceToken())
                    && token.getMemberId().equals(guest.id())), any());
            verify(fcmClient, after(300).never()).send(
                argThat((DeviceToken token) -> token != null && token.getMemberId().equals(host.id())), any());
        }

        @Test
        void FCM이_토큰_만료를_알려_오면_그_토큰을_지운다() throws Exception {
            given(fcmClient.send(sentTo(host), any())).willReturn(PushSendResult.EXPIRED);

            requestUpload(guest).andExpect(status().isAccepted());

            await().atMost(ASYNC_WAIT).untilAsserted(() -> assertThat(savedTokens())
                .doesNotContain(host.deviceToken())
                .contains(otherGuest.deviceToken()));
        }

        @Test
        void 한_기기에_발송이_실패해도_나머지_기기에는_보낸다() throws Exception {
            given(fcmClient.send(sentTo(host), any())).willThrow(new IllegalStateException("fcm down"));

            requestUpload(guest).andExpect(status().isAccepted());

            verify(fcmClient, timeout(ASYNC_WAIT.toMillis())).send(sentTo(otherGuest), any());
        }

        @Test
        void FCM_키가_없으면_앱으로는_보내지_않고_웹으로는_보낸다() throws Exception {
            given(fcmClient.enabled()).willReturn(false);
            String endpoint = "https://fcm.googleapis.com/fcm/send/" + UUID.randomUUID();
            subscribeWebPush(host, endpoint);

            requestUpload(guest).andExpect(status().isAccepted());

            verify(webPushClient, timeout(ASYNC_WAIT.toMillis())).send(
                argThat((PushSubscription subscription) -> subscription != null
                    && subscription.getEndpoint().equals(endpoint)), anyString());
            verify(fcmClient, after(300).never()).send(sentTo(host), any());
        }

        @Test
        void VAPID_키가_없어도_앱으로는_보낸다() throws Exception {
            given(webPushClient.enabled()).willReturn(false);

            requestUpload(guest).andExpect(status().isAccepted());

            verify(fcmClient, timeout(ASYNC_WAIT.toMillis())).send(sentTo(host), any());
        }
    }

    @Nested
    class 업로드_알림 {

        @Test
        void 올린_사진_수와_함께_다른_참여자_기기로_간다() throws Exception {
            given(fileStorage.findUploaded(any())).willReturn(Optional.of(new UploadedObject(1024, "image/jpeg")));
            disableNotification(otherGuest);

            completeUpload(guest, List.of(reservedMedia(guest), reservedMedia(guest)))
                .andExpect(status().isCreated());

            RoomNotification notification = sentNotification(host);
            assertThat(notification.type()).isEqualTo(RoomNotificationType.MEDIA_UPLOADED);
            assertThat(notification.body()).isEqualTo("민수님이 사진 2장을 올렸어요");
            assertThat(notification.roomId()).isEqualTo(room.getId());
            verify(fcmClient, after(300).never()).send(sentTo(guest), any());
            verify(fcmClient, never()).send(sentTo(otherGuest), any());
            verify(fcmClient, never()).send(sentTo(stranger), any());
        }
    }

    @Nested
    class 기기_토큰 {

        @Test
        void 등록하면_플랫폼과_함께_저장된다() throws Exception {
            String token = "android-" + UUID.randomUUID();

            registerToken(guest.authorization(), token, "android").andExpect(status().isNoContent());

            assertThat(deviceTokenJpaRepository.findAll())
                .filteredOn(saved -> saved.getToken().equals(token))
                .singleElement()
                .satisfies(saved -> {
                    assertThat(saved.getMemberId()).isEqualTo(guest.id());
                    assertThat(saved.getPlatform()).isEqualTo(DevicePlatform.ANDROID);
                });
        }

        @Test
        void 같은_토큰을_다시_등록해도_하나만_남는다() throws Exception {
            registerToken(guest.authorization(), guest.deviceToken(), "ios").andExpect(status().isNoContent());

            assertThat(savedTokens()).filteredOn(token -> token.equals(guest.deviceToken())).hasSize(1);
        }

        @Test
        void 지원하지_않는_플랫폼은_400() throws Exception {
            registerToken(guest.authorization(), "token-1", "web")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_DEVICE_TOKEN"));
        }

        @Test
        void 형식이_틀린_토큰은_400() throws Exception {
            registerToken(guest.authorization(), "not a token", "ios")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_DEVICE_TOKEN"));
        }

        @Test
        void 해제하면_더는_알림이_가지_않는다() throws Exception {
            unregisterToken(host, host.deviceToken()).andExpect(status().isNoContent());

            requestUpload(guest).andExpect(status().isAccepted());

            verify(fcmClient, timeout(ASYNC_WAIT.toMillis())).send(sentTo(otherGuest), any());
            verify(fcmClient, after(300).never()).send(sentTo(host), any());
        }

        @Test
        void 남의_토큰은_해제되지_않는다() throws Exception {
            unregisterToken(guest, host.deviceToken()).andExpect(status().isNoContent());

            assertThat(savedTokens()).contains(host.deviceToken());
        }

        @Test
        void 인증_없이는_등록할_수_없다() throws Exception {
            mockMvc.perform(post("/api/v1/device-tokens")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"token\":\"t\",\"platform\":\"ios\"}"))
                .andExpect(status().isUnauthorized());
        }
    }
}
