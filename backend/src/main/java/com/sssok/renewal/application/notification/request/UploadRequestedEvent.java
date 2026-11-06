package com.sssok.renewal.application.notification.request;

// 방 SSE payload 로 그대로 나간다. 문구는 푸시 알림과 같은 것을 쓴다.
public record UploadRequestedEvent(Long roomId, String roomCode, Long requesterId, String title, String body) {
}
