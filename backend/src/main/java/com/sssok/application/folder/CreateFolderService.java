package com.sssok.application.folder;

import com.sssok.application.folder.exception.DuplicateFolderNameException;
import com.sssok.application.port.out.FolderRepository;
import com.sssok.domain.folder.Folder;
import com.sssok.domain.folder.FolderName;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 폴더 생성. 방 존재/만료/입장 여부는 RoomMembershipInterceptor가 먼저 걸러준다.
@Service
@RequiredArgsConstructor
public class CreateFolderService {

    private final FolderRepository folderRepository;

    @Transactional
    public Folder create(Long roomId, String name) {
        FolderName folderName = new FolderName(name);
        if (folderRepository.findByRoomIdAndName(roomId, folderName.value()).isPresent()) {
            throw new DuplicateFolderNameException();
        }
        try {
            return folderRepository.save(Folder.create(roomId, folderName, Instant.now()));
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateFolderNameException();
        }
    }
}
