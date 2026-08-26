package com.sssok.application.folder;

import com.sssok.application.folder.exception.FolderNotFoundException;
import com.sssok.application.port.out.FolderMediaRepository;
import com.sssok.application.port.out.FolderRepository;
import com.sssok.domain.folder.Folder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 폴더 삭제. 방 존재/만료/입장 여부는 RoomMembershipInterceptor가 먼저 걸러준다.
// 폴더에 담겼던 미디어는 지우지 않고, folder_media에서 이 폴더가 맺은 관계만 끊는다.
@Service
@RequiredArgsConstructor
public class DeleteFolderService {

    private final FolderRepository folderRepository;
    private final FolderMediaRepository folderMediaRepository;

    @Transactional
    public DeleteFolderResult delete(Long roomId, Long folderId) {
        Folder folder = folderRepository.findById(folderId)
            .filter(f -> f.belongsTo(roomId))
            .orElseThrow(() -> new FolderNotFoundException(folderId));

        long detachedCount = folderMediaRepository.detachAllFromFolder(folder.getId());
        folderRepository.deleteById(folder.getId());

        return new DeleteFolderResult(folder.getId(), (int) detachedCount);
    }
}
