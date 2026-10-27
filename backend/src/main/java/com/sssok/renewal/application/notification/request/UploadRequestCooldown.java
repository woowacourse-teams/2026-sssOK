package com.sssok.renewal.application.notification.request;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.sssok.renewal.application.notification.exception.UploadRequestCooldownException;
import com.sssok.renewal.outgoing.config.UploadRequestProperties;
import org.springframework.stereotype.Component;

// 서버 메모리에만 두므로 단일 인스턴스 전제다(SSE 발행기와 같은 전제). 재기동하면 초기화된다.
@Component
public class UploadRequestCooldown {
    private final Cache<String, Boolean> lastRequests;

    public UploadRequestCooldown(UploadRequestProperties properties) {
        this.lastRequests = Caffeine.newBuilder()
            .expireAfterWrite(properties.cooldown())
            .build();
    }

    public void acquire(Long roomId, Long requesterId) {
        // putIfAbsent 로 확인과 기록을 한 번에 해, 동시에 두 번 눌러도 하나만 통과한다.
        if (lastRequests.asMap().putIfAbsent(roomId + ":" + requesterId, Boolean.TRUE) != null) {
            throw new UploadRequestCooldownException();
        }
    }
}
