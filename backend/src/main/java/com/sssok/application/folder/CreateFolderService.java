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
// 이름 중복은 조회로 먼저 걸러내되, uk_folder_room_id_name 제약을 최종 안전망으로 둔다 —
// 동시에 같은 이름으로 두 요청이 와서 둘 다 조회를 통과하더라도, 나중 INSERT가 제약을 위반하며
// 500이 아니라 정상적으로 409로 응답하게 한다.
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
