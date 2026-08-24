package com.sssok.application.room;

import com.sssok.application.port.out.RoomRepository;
import com.sssok.application.room.exception.RoomAlreadyDeletedException;
import com.sssok.application.room.exception.RoomNotFoundException;
import com.sssok.domain.room.Room;
import com.sssok.domain.room.RoomPermissionPolicy;
import com.sssok.domain.room.exception.RoomHostRequiredException;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 방 삭제
@Service
@RequiredArgsConstructor
public class DeleteRoomService {

    private final RoomRepository roomRepository;

    private final RoomPermissionPolicy permissionPolicy = new RoomPermissionPolicy();

    @Transactional
    public DeleteRoomResult delete(Long roomId, Long requesterId) {
        Instant now = Instant.now();
        Room room = roomRepository.findById(roomId)
            .orElseThrow(() -> new RoomNotFoundException(roomId));
        if (!permissionPolicy.canDeleteRoom(room, requesterId)) {
            throw new RoomHostRequiredException();
        }
        if (!room.canEnter(now)) {
            throw new RoomAlreadyDeletedException();
        }

        room.delete(requesterId, now);
        Room saved = roomRepository.save(room);
        return new DeleteRoomResult(saved.getDeletedAt(), saved.purgeAt());
    }
}
