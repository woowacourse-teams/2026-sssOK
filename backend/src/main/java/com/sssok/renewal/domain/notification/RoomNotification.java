package com.sssok.renewal.domain.notification;

// 서비스 워커가 그대로 띄우는 알림 내용. roomId 는 이미 그 방을 보고 있는 탭이면 알림을 거르는 데 쓴다.
public record RoomNotification(
    RoomNotificationType type,
    Long roomId,
    String roomCode,
    String title,
    String body,
    String url
) {
    private static final String DEFAULT_NAME = "참여자";

    public static RoomNotification mediaUploaded(Long roomId, String roomCode, String roomName,
                                                 String uploaderName, int count) {
        String body = count == 1
            ? "%s님이 사진을 올렸어요".formatted(nameOrDefault(uploaderName))
            : "%s님이 사진 %d장을 올렸어요".formatted(nameOrDefault(uploaderName), count);
        return new RoomNotification(RoomNotificationType.MEDIA_UPLOADED, roomId, roomCode, roomName, body,
            galleryUrl(roomCode));
    }

    public static RoomNotification uploadRequested(Long roomId, String roomCode, String requesterName) {
        String name = nameOrDefault(requesterName);
        return new RoomNotification(RoomNotificationType.UPLOAD_REQUESTED, roomId, roomCode,
            "%s님이 알림을 보냈습니다".formatted(name), "%s님의 사진을 올려주세요!".formatted(name),
            galleryUrl(roomCode));
    }

    private static String nameOrDefault(String name) {
        return name == null || name.isBlank() ? DEFAULT_NAME : name;
    }

    private static String galleryUrl(String roomCode) {
        return "/rooms/" + roomCode + "/gallery";
    }
}
