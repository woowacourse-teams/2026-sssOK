package com.sssok.application.room;

import com.sssok.application.port.out.EventPublisherPort;
import com.sssok.application.port.out.RoomRepository;
import com.sssok.application.room.exception.EmptyPatchException;
import com.sssok.application.room.exception.RoomExpiredException;
import com.sssok.application.room.exception.RoomNotFoundException;
import com.sssok.domain.room.Room;
import com.sssok.domain.room.RoomExpiration;
import com.sssok.domain.room.RoomName;
import com.sssok.domain.room.RoomPermissionPolicy;
import com.sssok.domain.room.UploadPolicy;
import com.sssok.domain.room.exception.RoomHostRequiredException;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 방 이름, 만료, 업로드 권한 변경
@Service
@RequiredArgsConstructor
public class UpdateRoomService {

    private final RoomRepository roomRepository;
    private final RoomDetailReader roomDetailReader;
    private final EventPublisherPort eventPublisher;

    private final RoomPermissionPolicy permissionPolicy = new RoomPermissionPolicy();

    @Transactional
    public RoomDetail update(Long roomId, Long requesterId, UpdateRoomCommand command) {
        if (command.isEmpty()) {
            throw new EmptyPatchException();
        }

        Instant now = Instant.now();
        Room room = roomRepository.findById(roomId)
            .orElseThrow(() -> new RoomNotFoundException(roomId));
        if (!permissionPolicy.canChangeSettings(room, requesterId)) {
            throw new RoomHostRequiredException();
        }
        if (!room.canEnter(now)) {
            throw new RoomExpiredException();
        }

        room.updateSettings(requesterId, resolveName(room, command), resolveExpiration(room, command, now),
            resolveUploadPolicy(room, command));
        Room saved = roomRepository.save(room);
        eventPublisher.publish(roomId, "room.updated", RoomUpdatedEvent.from(saved));
        return roomDetailReader.read(saved, requesterId);
    }

    private RoomName resolveName(Room room, UpdateRoomCommand command) {
        if (command.name() == null) {
            return room.getName();
        }
        return new RoomName(command.name());
    }

    private RoomExpiration resolveExpiration(Room room, UpdateRoomCommand command, Instant now) {
        if (command.expiryHours() == null) {
            return room.getExpiration();
        }
        return RoomExpiration.from(now, command.expiryHours());
    }

    private UploadPolicy resolveUploadPolicy(Room room, UpdateRoomCommand command) {
        if (command.uploadPolicy() == null) {
            return room.getUploadPolicy();
        }
        return UploadPolicy.from(command.uploadPolicy());
    }
}
