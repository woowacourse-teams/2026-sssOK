package com.sssok.application.folder;

import com.sssok.application.folder.exception.DuplicateFolderNameException;
import com.sssok.application.folder.exception.FolderNotFoundException;
import com.sssok.application.port.out.FolderRepository;
import com.sssok.domain.folder.Folder;
import com.sssok.domain.folder.FolderName;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// 폴더 이름 변경. 방 존재/만료/입장 여부는 RoomMembershipInterceptor가 먼저 걸러준다.
@Service
@RequiredArgsConstructor
public class RenameFolderService {

    private final FolderRepository folderRepository;

    public Folder rename(Long roomId, Long folderId, String newName) {
        Folder folder = folderRepository.findById(folderId)
            .filter(f -> f.belongsTo(roomId))
            .orElseThrow(() -> new FolderNotFoundException(folderId));

        FolderName newFolderName = new FolderName(newName);
        folderRepository.findByRoomIdAndName(roomId, newFolderName.value())
            .filter(other -> !other.getId().equals(folderId))
            .ifPresent(other -> {
                throw new DuplicateFolderNameException();
            });

        folder.rename(newFolderName);
        return folderRepository.save(folder);
    }
}
