package com.sssok.infrastructure.room;

import com.sssok.application.port.out.RoomPermissionPort;
import com.sssok.application.port.out.RoomRepository;
import com.sssok.application.room.exception.RoomNotFoundException;
import com.sssok.domain.room.Room;
import com.sssok.domain.room.RoomPermissionPolicy;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RoomPermissionPortAdapter implements RoomPermissionPort {

    private final RoomRepository roomRepository;

    private final RoomPermissionPolicy permissionPolicy = new RoomPermissionPolicy();

    @Override
    public boolean isHost(Long roomId, Long memberId) {
        return findRoom(roomId)
                .map(room -> room.isHost(memberId))
                .orElse(false);
    }

    @Override
    public boolean canUpload(Long roomId, Long memberId) {
        return findRoom(roomId)
                .map(room -> permissionPolicy.canUploadTo(room, memberId, Instant.now()))
                .orElse(false);
    }

    @Override
    public boolean isRoomUsable(Long roomId) {
        return findRoom(roomId)
                .map(room -> room.canEnter(Instant.now()))
                .orElse(false);
    }

    @Override
    public String getRoomCode(Long roomId) {
        return findRoom(roomId)
                .map(room -> room.getCode().value())
                .orElseThrow(() -> new RoomNotFoundException(roomId));
    }

    private Optional<Room> findRoom(Long roomId) {
        return roomRepository.findById(roomId);
    }
}
