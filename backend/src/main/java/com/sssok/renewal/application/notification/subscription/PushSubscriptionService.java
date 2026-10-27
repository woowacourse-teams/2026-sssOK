package com.sssok.renewal.application.notification.subscription;

import com.sssok.renewal.application.notification.exception.WebPushUnavailableException;
import com.sssok.renewal.domain.notification.PushSubscription;
import com.sssok.renewal.outgoing.database.PushSubscriptionRepository;
import com.sssok.renewal.outgoing.push.WebPushClient;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// 구독은 방이 아니라 회원(브라우저)에 붙는다. 어느 방 알림을 받을지는 방 참여 여부와 방별 설정으로 정한다.
@Service
@RequiredArgsConstructor
public class PushSubscriptionService {
    private final PushSubscriptionRepository pushSubscriptionRepository;
    private final WebPushClient webPushClient;

    public String publicKey() {
        if (!webPushClient.enabled()) {
            throw new WebPushUnavailableException();
        }
        return webPushClient.publicKey();
    }

    public void register(RegisterPushSubscriptionCommand command) {
        PushSubscription subscription = PushSubscription.create(
            command.memberId(), command.endpoint(), command.p256dh(), command.auth());
        pushSubscriptionRepository.save(subscription, Instant.now());
    }

    public void unregister(Long memberId, String endpoint) {
        pushSubscriptionRepository.delete(memberId, endpoint);
    }
}
