package com.sssok.application.room;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

import com.sssok.application.port.out.FileRepository;
import com.sssok.application.port.out.FileStoragePort;
import com.sssok.application.port.out.FolderMediaRepository;
import com.sssok.application.port.out.FolderRepository;
import com.sssok.application.port.out.LinkCodeRepository;
import com.sssok.application.port.out.MemberRepository;
import com.sssok.application.port.out.RoomEventRepository;
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
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

// 지우는 순서 자체가 요구사항이라 호출 순서를 직접 확인한다.
class RoomPurgerTest {

    private static final Instant NOW = Instant.parse("2026-08-13T00:00:00Z");
    private static final Long ROOM_ID = 10L;
    private static final Long HOST_ID = 1L;

    private final RoomRepository roomRepository = mock(RoomRepository.class);
    private final RoomMemberRepository roomMemberRepository = mock(RoomMemberRepository.class);
    private final RoomEventRepository roomEventRepository = mock(RoomEventRepository.class);
    private final MemberRepository memberRepository = mock(MemberRepository.class);
    private final LinkCodeRepository linkCodeRepository = mock(LinkCodeRepository.class);
    private final FileRepository fileRepository = mock(FileRepository.class);
    private final FolderRepository folderRepository = mock(FolderRepository.class);
    private final FolderMediaRepository folderMediaRepository = mock(FolderMediaRepository.class);
    private final FileStoragePort fileStoragePort = mock(FileStoragePort.class);

    private final RoomPurger roomPurger = new RoomPurger(
        roomRepository, roomMemberRepository, roomEventRepository, memberRepository, linkCodeRepository,
        fileRepository, folderRepository, folderMediaRepository, fileStoragePort);

    @Test
    void 실물을_먼저_지우고_방을_마지막에_지운다() {
        Room room = deletedRoom();
        StoredFile file = StoredFile.reserve(ROOM_ID, HOST_ID, "photo.jpg", "image/jpeg",
            FileSize.ofMegabytes(1), NOW);
        given(fileRepository.findAllByRoomId(ROOM_ID)).willReturn(List.of(file));

        roomPurger.purge(room);

        InOrder order = inOrder(fileStoragePort, fileRepository, folderMediaRepository, folderRepository,
            roomMemberRepository, roomEventRepository, roomRepository, linkCodeRepository, memberRepository);
        // 참여자 명단은 참여 기록을 지우기 전에 확보해야 한다.
        order.verify(roomMemberRepository).findMemberIdsByRoomId(ROOM_ID);
        order.verify(fileStoragePort).delete(file.getStorageKey());
        order.verify(fileRepository).deleteAllByRoomId(ROOM_ID);
        // folder_media는 folder를 지우기 전에 먼저 관계를 끊어야 고아 행이 안 남는다.
        order.verify(folderMediaRepository).detachAllByRoomId(ROOM_ID);
        order.verify(folderRepository).deleteAllByRoomId(ROOM_ID);
        order.verify(roomEventRepository).deleteAllByRoomId(ROOM_ID);
        order.verify(roomMemberRepository).deleteAllByRoomId(ROOM_ID);
        order.verify(roomRepository).delete(room);
        // 방을 지운 뒤에 판정해야 지금 지운 방을 "아직 남은 방"으로 세지 않는다.
        order.verify(roomRepository).findHostIdsIn(Set.of(HOST_ID));
        order.verify(linkCodeRepository).deleteAllByMemberIdIn(Set.of(HOST_ID));
        order.verify(memberRepository).deleteAllByIdIn(Set.of(HOST_ID));
    }

    private Room deletedRoom() {
        Instant deletedAt = NOW.minus(Duration.ofDays(10));
        return Room.reconstruct(ROOM_ID, 0L, new RoomCode("A3F9K2M7"), new RoomName("지난 회식"),
            RoomStatus.from("DELETED"), new RoomExpiration(deletedAt), UploadPolicy.ANYONE,
            HOST_ID, deletedAt.minus(Duration.ofDays(1)), deletedAt);
    }
}
