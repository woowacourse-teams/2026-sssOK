package com.sssok.application.room;

import com.sssok.application.port.out.FileRepository;
import com.sssok.application.port.out.FileStoragePort;
import com.sssok.application.port.out.FolderMediaRepository;
import com.sssok.application.port.out.FolderRepository;
import com.sssok.application.port.out.LinkCodeRepository;
import com.sssok.application.port.out.MemberRepository;
import com.sssok.application.port.out.RoomEventRepository;
import com.sssok.application.port.out.RoomMemberRepository;
import com.sssok.application.port.out.RoomRepository;
import com.sssok.domain.file.StoredFile;
import com.sssok.domain.room.Room;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// 방 하나를 지우는 단위. 방마다 트랜잭션을 따로 열어, 한 방이 실패해도 나머지가 함께 되돌아가지 않게 한다.
@Component
@RequiredArgsConstructor
public class RoomPurger {

    private final RoomRepository roomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final RoomEventRepository roomEventRepository;
    private final MemberRepository memberRepository;
    private final LinkCodeRepository linkCodeRepository;
    private final FileRepository fileRepository;
    private final FolderRepository folderRepository;
    private final FolderMediaRepository folderMediaRepository;
    private final FileStoragePort fileStoragePort;

    // 실물을 먼저 지운다. DB 로우가 남아 있어야 중간에 실패해도 다음 회차에 다시 찾아 시도할 수 있다.
    // 반대로 DB를 먼저 지우면 어떤 실물을 지워야 하는지 알 방법이 사라진다.
    @Transactional
    public void purge(Room room) {
        Set<Long> members = membersOf(room);

        fileRepository.findAllByRoomId(room.getId()).forEach(this::deleteFromStorage);

        fileRepository.deleteAllByRoomId(room.getId());
        // 폴더가 미디어와 맺은 관계를 먼저 끊어야, 폴더를 지운 뒤 folder_media에 고아 행이 남지 않는다.
        folderMediaRepository.detachAllByRoomId(room.getId());
        folderRepository.deleteAllByRoomId(room.getId());
        roomEventRepository.deleteAllByRoomId(room.getId());
        roomMemberRepository.deleteAllByRoomId(room.getId());
        roomRepository.delete(room);

        purgeMembers(members);
    }

    // 원본과 썸네일은 서로 다른 키라 각각 지워야 한다. 하나만 지우면 남은 쪽이 영영 요금을 먹는다.
    private void deleteFromStorage(StoredFile file) {
        fileStoragePort.delete(file.getStorageKey());
        if (file.getThumbnailKey() != null) {
            fileStoragePort.delete(file.getThumbnailKey());
        }
    }

    // 방장도 생성 시점에 참여자로 등록되지만, 그 전에 만들어진 방은 기록이 없어 따로 넣는다.
    private Set<Long> membersOf(Room room) {
        Set<Long> members = new LinkedHashSet<>(roomMemberRepository.findMemberIdsByRoomId(room.getId()));
        members.add(room.getHostId());
        return members;
    }

    // 방마다 익명 인증을 새로 하므로 회원은 방 하나에 매인다. 그 방이 사라지면 다시 그 회원으로
    // 인증할 방법이 없어 도달할 수 없는 행이 된다.
    // 다만 토큰을 여러 방에 쓴 경우가 있을 수 있어, 남은 방과 이어진 사람은 빼고 지운다.
    private void purgeMembers(Set<Long> members) {
        members.removeAll(roomMemberRepository.findMemberIdsStillInAnyRoom(members));
        members.removeAll(roomRepository.findHostIdsIn(members));

        linkCodeRepository.deleteAllByMemberIdIn(members);
        memberRepository.deleteAllByIdIn(members);
    }
}