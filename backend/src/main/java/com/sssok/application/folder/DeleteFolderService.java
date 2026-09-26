package com.sssok.application.folder;

import com.sssok.application.folder.exception.FolderNotFoundException;
import com.sssok.application.port.out.FolderMediaRepository;
import com.sssok.application.port.out.FolderRepository;
import com.sssok.domain.file.UploadStatus;
import com.sssok.domain.folder.Folder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 폴더 삭제. 방 존재/만료/입장 여부는 RoomMembershipInterceptor가 먼저 걸러준다.
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

        // 매핑 행이 아니라 목록에 보이던 사진 수를 센다 — 사용자가 이 폴더에서 보던 개수이자,
        // 꺼내진 뒤 루트에서 보게 될 개수다. 관계는 실물 없는 행까지 전부 끊는다.
        long detachedPhotoCount = folderMediaRepository.countByFolderIdAndStatusIn(
            folder.getId(), UploadStatus.visibleStatuses());
        folderMediaRepository.detachAllFromFolder(folder.getId());
        folderRepository.deleteById(folder.getId());

        return new DeleteFolderResult(folder.getId(), (int) detachedPhotoCount);
    }
}
