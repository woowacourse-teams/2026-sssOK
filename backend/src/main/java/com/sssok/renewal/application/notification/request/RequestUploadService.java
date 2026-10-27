package com.sssok.renewal.application.notification.request;

import com.sssok.application.room.exception.RoomNotFoundException;
import com.sssok.domain.room.Room;
import com.sssok.renewal.application.notification.send.RoomNotifier;
import com.sssok.renewal.domain.notification.RoomNotification;
import com.sssok.renewal.outgoing.database.NotificationMemberRepository;
import com.sssok.renewal.outgoing.database.NotificationRoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RequestUploadService {
    private final NotificationRoomRepository roomRepository;
    private final NotificationMemberRepository memberRepository;
    private final UploadRequestCooldown cooldown;
    private final RoomNotifier roomNotifier;

    public void request(Long roomId, Long requesterId) {
        Room room = roomRepository.findById(roomId).orElseThrow(() -> new RoomNotFoundException(roomId));
        cooldown.acquire(roomId, requesterId);
        String requesterName = memberRepository.findNickname(requesterId).orElse(null);
        roomNotifier.notifyMembers(roomId, requesterId, RoomNotification.uploadRequested(
            roomId, room.getCode().value(), requesterName));
    }
}
