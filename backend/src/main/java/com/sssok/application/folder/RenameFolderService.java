package com.sssok.application.folder;

import com.sssok.application.folder.exception.DuplicateFolderNameException;
import com.sssok.application.folder.exception.FolderNotFoundException;
import com.sssok.application.port.out.FolderRepository;
import com.sssok.domain.folder.Folder;
import com.sssok.domain.folder.FolderName;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 폴더 이름 변경. 방 존재/만료/입장 여부는 RoomMembershipInterceptor가 먼저 걸러준다.
// 이름 중복은 조회로 먼저 걸러내되, uk_folder_room_id_name 제약을 최종 안전망으로 둔다 —
// CreateFolderService와 같은 이유로, 동시에 같은 이름으로 바꾸는 두 요청이 와도 500이 아니라 409로 응답하게 한다.
@Service
@RequiredArgsConstructor
public class RenameFolderService {

    private final FolderRepository folderRepository;

    @Transactional
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
        try {
            return folderRepository.save(folder);
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateFolderNameException();
        }
    }
}
