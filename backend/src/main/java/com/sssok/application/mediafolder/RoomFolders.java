package com.sssok.application.mediafolder;

import com.sssok.application.folder.exception.FolderNotFoundException;
import com.sssok.application.port.out.FolderRepository;
import com.sssok.domain.folder.Folder;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// 담기/꺼내기 공통: 요청받은 folderIds가 전부 이 방 소속 폴더인지 확인한다.
// 하나라도 없거나 다른 방 소속이면 요청 전체를 거부한다(404).
@Component
@RequiredArgsConstructor
class RoomFolders {

    private final FolderRepository folderRepository;

    List<Folder> requireAllInRoom(Long roomId, List<Long> folderIds) {
        List<Folder> found = folderRepository.findAllById(folderIds).stream()
            .filter(folder -> folder.belongsTo(roomId))
            .toList();
        Set<Long> foundIds = found.stream().map(Folder::getId).collect(Collectors.toSet());
        List<Long> missing = folderIds.stream().filter(id -> !foundIds.contains(id)).toList();
        if (!missing.isEmpty()) {
            throw new FolderNotFoundException(missing);
        }
        return found;
    }
}
