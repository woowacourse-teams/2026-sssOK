package com.sssok.renewal.incoming.notification;

import com.sssok.renewal.application.media.upload.complete.MediaUploadedEvent;
import com.sssok.renewal.application.notification.send.UploadNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

// 업로드 완료 요청 스레드에서 불리므로 알림 쪽 오류가 업로드 응답을 실패시키지 않게 여기서 막는다.
@Slf4j
@Component
@RequiredArgsConstructor
public class MediaUploadedNotificationTrigger {
    private final UploadNotificationService uploadNotificationService;

    @EventListener
    public void onUploaded(MediaUploadedEvent event) {
        try {
            uploadNotificationService.notifyUploaded(
                event.roomId(), event.uploaderId(), event.uploaderName(), event.count());
        } catch (RuntimeException e) {
            log.warn("업로드 알림을 보내지 못했습니다. roomId={}", event.roomId(), e);
        }
    }
}
