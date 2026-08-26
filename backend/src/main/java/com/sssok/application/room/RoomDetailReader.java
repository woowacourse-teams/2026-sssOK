package com.sssok.application.room;

import com.sssok.application.port.out.FileRepository;
import com.sssok.application.port.out.FolderMediaRepository;
import com.sssok.application.port.out.FolderRepository;
import com.sssok.application.port.out.MemberRepository;
import com.sssok.application.port.out.RoomMemberRepository;
import com.sssok.domain.folder.Folder;
import com.sssok.domain.room.Room;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// 방 응답에 붙는 조회성 값을 한곳에서 채운다.
@Component
@RequiredArgsConstructor
public class RoomDetailReader {

    private final MemberRepository memberRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final FolderRepository folderRepository;
    private final FolderMediaRepository folderMediaRepository;
    private final FileRepository fileRepository;

    public RoomDetail read(Room room, Long requesterId) {
        return new RoomDetail(
            room,
            hostNameOf(room),
            isJoined(room, requesterId),
            photoCountOf(room),
            foldersOf(room)
        );
    }

    private String hostNameOf(Room room) {
        return memberRepository.findById(room.getHostId())
            .map(member -> member.getDisplayName().value())
            .orElse(null);
    }

    private boolean isJoined(Room room, Long requesterId) {
        return requesterId != null
            && roomMemberRepository.findByRoomIdAndMemberId(room.getId(), requesterId).isPresent();
    }

    // 폴더 소속과 무관하게 이 방에 있는 미디어 전체 수 — 갓 만든 방은 당연히 0이다.
    private int photoCountOf(Room room) {
        return fileRepository.findAllByRoomId(room.getId()).size();
    }

    private List<RoomFolderSummary> foldersOf(Room room) {
        return folderRepository.findAllByRoomId(room.getId()).stream()
            .map(this::toSummary)
            .toList();
    }

    private RoomFolderSummary toSummary(Folder folder) {
        long photoCount = folderMediaRepository.countByFolderId(folder.getId());
        return new RoomFolderSummary(folder.getId(), folder.getName().value(), folder.getCreatedAt(), (int) photoCount);
    }
}
