package com.sssok.application.room;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

import com.sssok.application.port.out.FileRepository;
import com.sssok.application.port.out.FileStoragePort;
import com.sssok.application.port.out.FolderRepository;
import com.sssok.application.port.out.RoomMemberRepository;
import com.sssok.application.port.out.RoomRepository;
import com.sssok.domain.file.FileSize;
import com.sssok.domain.file.StoredFile;
import com.sssok.domain.room.Room;
import com.sssok.domain.room.RoomCode;
import com.sssok.domain.room.RoomExpiration;
import com.sssok.domain.room.RoomName;
import com.sssok.domain.room.UploadPolicy;
import com.sssok.domain.room.roomstatus.RoomStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

// 지우는 순서 자체가 요구사항이라 호출 순서를 직접 확인한다.
class RoomPurgerTest {

    private static final Instant NOW = Instant.parse("2026-08-13T00:00:00Z");
    private static final Long ROOM_ID = 10L;
    private static final Long HOST_ID = 1L;

    private final RoomRepository roomRepository = mock(RoomRepository.class);
    private final RoomMemberRepository roomMemberRepository = mock(RoomMemberRepository.class);
    private final FileRepository fileRepository = mock(FileRepository.class);
    private final FolderRepository folderRepository = mock(FolderRepository.class);
    private final FileStoragePort fileStoragePort = mock(FileStoragePort.class);

    private final RoomPurger roomPurger = new RoomPurger(
        roomRepository, roomMemberRepository, fileRepository, folderRepository, fileStoragePort);

    @Test
    void 실물을_먼저_지우고_방을_마지막에_지운다() {
        Room room = deletedRoom();
        StoredFile file = StoredFile.beginUpload(ROOM_ID, HOST_ID, "photo.jpg",
            FileSize.ofMegabytes(1), null, NOW);
        given(fileRepository.findAllByRoomId(ROOM_ID)).willReturn(List.of(file));

        roomPurger.purge(room);

        InOrder order = inOrder(fileStoragePort, fileRepository, folderRepository,
            roomMemberRepository, roomRepository);
        order.verify(fileStoragePort).delete(file.getStorageKey());
        order.verify(fileRepository).deleteAllByRoomId(ROOM_ID);
        order.verify(folderRepository).deleteAllByRoomId(ROOM_ID);
        order.verify(roomMemberRepository).deleteAllByRoomId(ROOM_ID);
        order.verify(roomRepository).delete(room);
        order.verifyNoMoreInteractions();
    }

    private Room deletedRoom() {
        Instant deletedAt = NOW.minus(Duration.ofDays(10));
        return Room.reconstruct(ROOM_ID, 0L, new RoomCode("A3F9K2M7"), new RoomName("지난 회식"),
            RoomStatus.from("DELETED"), new RoomExpiration(deletedAt), UploadPolicy.ANYONE,
            HOST_ID, deletedAt.minus(Duration.ofDays(1)), deletedAt);
    }
}
