package com.sssok.renewal.application.notification.request;

import com.sssok.application.room.RoomNicknameReader;
import com.sssok.application.room.exception.RoomNotFoundException;
import com.sssok.domain.room.Room;
import com.sssok.renewal.application.notification.send.RoomNotifier;
import com.sssok.renewal.domain.notification.RoomNotification;
import com.sssok.renewal.outgoing.database.NotificationRoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RequestUploadService {
    private final NotificationRoomRepository roomRepository;
    private final RoomNicknameReader roomNicknameReader;
    private final UploadRequestCooldown cooldown;
    private final RoomNotifier roomNotifier;
    private final ApplicationEventPublisher eventPublisher;

    public void request(Long roomId, Long requesterId) {
        Room room = roomRepository.findById(roomId).orElseThrow(() -> new RoomNotFoundException(roomId));
        cooldown.acquire(roomId, requesterId);
        // 회원 전역 닉네임은 소셜 가입이면 "사용자", 익명이면 처음 입장한 방의 닉네임이라 이 방의 닉네임을 쓴다.
        String requesterName = roomNicknameReader.read(roomId, requesterId);
        RoomNotification notification = RoomNotification.uploadRequested(roomId, room.getCode().value(), requesterName);
        roomNotifier.notifyMembers(roomId, requesterId, notification);
        eventPublisher.publishEvent(new UploadRequestedEvent(
            roomId, notification.roomCode(), requesterId, notification.title(), notification.body()));
    }
}
