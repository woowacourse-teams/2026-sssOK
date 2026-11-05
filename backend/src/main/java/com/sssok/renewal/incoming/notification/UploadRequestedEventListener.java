package com.sssok.renewal.incoming.notification;

import com.sssok.application.port.out.EventPublisherPort;
import com.sssok.renewal.application.notification.request.UploadRequestedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

// 본인·알림 끈 참여자도 받는다(배너를 띄울지는 프론트가 거른다).
// 요청 API 스레드에서 불리므로 SSE 쪽 오류가 이미 접수한 요청을 실패시키지 않게 여기서 막는다.
@Slf4j
@Component
@RequiredArgsConstructor
public class UploadRequestedEventListener {
    static final String UPLOAD_REQUESTED = "upload.requested";

    private final EventPublisherPort eventPublisher;

    @EventListener
    public void handle(UploadRequestedEvent event) {
        try {
            eventPublisher.publish(event.roomId(), UPLOAD_REQUESTED, event);
        } catch (RuntimeException failure) {
            log.warn("업로드 요청 실시간 알림을 보내지 못했습니다. roomId={}", event.roomId(), failure);
        }
    }
}
