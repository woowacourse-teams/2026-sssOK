package com.sssok.application.room;

import com.sssok.application.port.out.FileRepository;
import com.sssok.application.port.out.FileStoragePort;
import com.sssok.application.port.out.FolderRepository;
import com.sssok.application.port.out.RoomMemberRepository;
import com.sssok.application.port.out.RoomRepository;
import com.sssok.domain.room.Room;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// 방 하나를 지우는 단위. 방마다 트랜잭션을 따로 열어, 한 방이 실패해도 나머지가 함께 되돌아가지 않게 한다.
@Component
@RequiredArgsConstructor
public class RoomPurger {

    private final RoomRepository roomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final FileRepository fileRepository;
    private final FolderRepository folderRepository;
    private final FileStoragePort fileStoragePort;

    // 실물을 먼저 지운다. DB 로우가 남아 있어야 중간에 실패해도 다음 회차에 다시 찾아 시도할 수 있다.
    // 반대로 DB를 먼저 지우면 어떤 실물을 지워야 하는지 알 방법이 사라진다.
    @Transactional
    public void purge(Room room) {
        fileRepository.findAllByRoomId(room.getId())
            .forEach(file -> fileStoragePort.delete(file.getStorageKey()));

        fileRepository.deleteAllByRoomId(room.getId());
        folderRepository.deleteAllByRoomId(room.getId());
        roomMemberRepository.deleteAllByRoomId(room.getId());
        roomRepository.delete(room);
    }
}
