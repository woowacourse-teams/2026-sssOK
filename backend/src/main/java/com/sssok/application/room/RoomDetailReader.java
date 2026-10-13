package com.sssok.application.room;

import com.sssok.application.port.out.FileRepository;
import com.sssok.application.port.out.FolderMediaRepository;
import com.sssok.application.port.out.FolderRepository;
import com.sssok.application.port.out.MemberRepository;
import com.sssok.application.port.out.RoomMemberRepository;
import com.sssok.domain.file.UploadStatus;
import com.sssok.domain.folder.Folder;
import com.sssok.domain.room.Room;
import java.util.List;
import java.util.Map;
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

    // 폴더 소속과 무관하게 이 방에서 목록에 노출되는 미디어 수 — 갓 만든 방은 당연히 0이다.
    // 폴더 photoCount 와 같은 기준을 써야 목록 개수와 어긋나지 않는다.
    private int photoCountOf(Room room) {
        return (int) fileRepository.countByRoomIdAndStatusIn(room.getId(), UploadStatus.visibleStatuses());
    }

    // 폴더마다 개수를 따로 세면 폴더 수만큼 쿼리가 늘어나, 한 번에 세서 나눠 담는다.
    private List<RoomFolderSummary> foldersOf(Room room) {
        List<Folder> folders = folderRepository.findAllByRoomId(room.getId());
        Map<Long, Long> photoCounts = folderMediaRepository.countByFolderIdsAndStatusIn(
            folders.stream().map(Folder::getId).toList(), UploadStatus.visibleStatuses());
        return folders.stream()
            .map(folder -> toSummary(folder, photoCounts.getOrDefault(folder.getId(), 0L)))
            .toList();
    }

    private RoomFolderSummary toSummary(Folder folder, long photoCount) {
        return new RoomFolderSummary(folder.getId(), folder.getName().value(), folder.getCreatedAt(), (int) photoCount);
    }
}
