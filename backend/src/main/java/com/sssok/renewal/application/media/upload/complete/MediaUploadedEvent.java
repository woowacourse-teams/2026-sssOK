package com.sssok.renewal.application.media.upload.complete;

// 업로드 완료 요청 한 번에 하나. 여러 장을 올려도 알림이 한 번만 가게 묶는다.
public record MediaUploadedEvent(Long roomId, Long uploaderId, String uploaderName, int count) {
}
