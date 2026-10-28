package com.sssok.renewal.application.notification.send;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sssok.renewal.domain.notification.DeviceToken;
import com.sssok.renewal.domain.notification.PushSubscription;
import com.sssok.renewal.domain.notification.RoomNotification;
import com.sssok.renewal.outgoing.database.DeviceTokenRepository;
import com.sssok.renewal.outgoing.database.PushSubscriptionRepository;
import com.sssok.renewal.outgoing.push.FcmClient;
import com.sssok.renewal.outgoing.push.PushSendResult;
import com.sssok.renewal.outgoing.push.WebPushClient;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Component;

// 푸시 서비스가 느리거나 죽어 있어도 API 응답에 영향이 없도록 별도 스레드에서 보낸다.
// 실패한 알림은 다시 보내지 않는다(재시도하면 중복 알림을 함께 다뤄야 한다).
// 받는 사람은 같고, 브라우저 구독은 Web Push 로, 앱 기기 토큰은 FCM 으로 나눠 보낸다.
@Slf4j
@Component
public class RoomNotifier {
    private final PushSubscriptionRepository pushSubscriptionRepository;
    private final DeviceTokenRepository deviceTokenRepository;
    private final WebPushClient webPushClient;
    private final FcmClient fcmClient;
    private final ObjectMapper objectMapper;
    private final AsyncTaskExecutor taskExecutor;

    public RoomNotifier(PushSubscriptionRepository pushSubscriptionRepository,
                        DeviceTokenRepository deviceTokenRepository,
                        WebPushClient webPushClient, FcmClient fcmClient, ObjectMapper objectMapper,
                        @Qualifier("roomNotificationTaskExecutor") AsyncTaskExecutor taskExecutor) {
        this.pushSubscriptionRepository = pushSubscriptionRepository;
        this.deviceTokenRepository = deviceTokenRepository;
        this.webPushClient = webPushClient;
        this.fcmClient = fcmClient;
        this.objectMapper = objectMapper;
        this.taskExecutor = taskExecutor;
    }

    public void notifyMembers(Long roomId, Long actorId, RoomNotification notification) {
        if (!webPushClient.enabled() && !fcmClient.enabled()) {
            return;
        }
        try {
            taskExecutor.execute(() -> send(roomId, actorId, notification));
        } catch (TaskRejectedException e) {
            log.warn("방 알림 작업을 제출하지 못해 건너뜁니다. roomId={}, type={}", roomId, notification.type());
        }
    }

    // 한쪽 경로를 준비하다 실패해도 다른 경로로는 보낸다.
    private void send(Long roomId, Long actorId, RoomNotification notification) {
        if (webPushClient.enabled()) {
            sendWebPush(roomId, actorId, notification);
        }
        if (fcmClient.enabled()) {
            sendApp(roomId, actorId, notification);
        }
    }

    private void sendWebPush(Long roomId, Long actorId, RoomNotification notification) {
        String payload;
        List<PushSubscription> recipients;
        try {
            payload = objectMapper.writeValueAsString(notification);
            recipients = pushSubscriptionRepository.findRecipients(roomId, actorId);
        } catch (JsonProcessingException | RuntimeException e) {
            log.warn("방 알림을 준비하지 못해 건너뜁니다. roomId={}, type={}", roomId, notification.type(), e);
            return;
        }
        for (PushSubscription recipient : recipients) {
            sendTo(recipient, payload);
        }
    }

    private void sendApp(Long roomId, Long actorId, RoomNotification notification) {
        List<DeviceToken> recipients;
        try {
            recipients = deviceTokenRepository.findRecipients(roomId, actorId);
        } catch (RuntimeException e) {
            log.warn("앱 알림을 준비하지 못해 건너뜁니다. roomId={}, type={}", roomId, notification.type(), e);
            return;
        }
        for (DeviceToken recipient : recipients) {
            sendTo(recipient, notification);
        }
    }

    // 한 사람에게 보내다 실패해도 나머지 참여자에게는 계속 보낸다.
    private void sendTo(PushSubscription recipient, String payload) {
        try {
            if (webPushClient.send(recipient, payload) == PushSendResult.EXPIRED) {
                pushSubscriptionRepository.deleteExpired(recipient.getEndpoint());
            }
        } catch (RuntimeException e) {
            log.warn("방 알림 발송 중 오류가 나 건너뜁니다. memberId={}", recipient.getMemberId(), e);
        }
    }

    private void sendTo(DeviceToken recipient, RoomNotification notification) {
        try {
            if (fcmClient.send(recipient, notification) == PushSendResult.EXPIRED) {
                deviceTokenRepository.deleteExpired(recipient.getToken());
            }
        } catch (RuntimeException e) {
            log.warn("앱 알림 발송 중 오류가 나 건너뜁니다. memberId={}", recipient.getMemberId(), e);
        }
    }
}
