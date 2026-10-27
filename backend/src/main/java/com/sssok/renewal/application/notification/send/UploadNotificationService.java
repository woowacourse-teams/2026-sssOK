package com.sssok.renewal.application.notification.send;

import com.sssok.renewal.domain.notification.RoomNotification;
import com.sssok.renewal.outgoing.database.NotificationRoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UploadNotificationService {
    private final NotificationRoomRepository roomRepository;
    private final RoomNotifier roomNotifier;

    public void notifyUploaded(Long roomId, Long uploaderId, String uploaderName, int count) {
        roomRepository.findById(roomId).ifPresent(room -> roomNotifier.notifyMembers(roomId, uploaderId,
            RoomNotification.mediaUploaded(roomId, room.getCode().value(), room.getName().value(), uploaderName, count)));
    }
}
