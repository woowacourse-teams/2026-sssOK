package com.sssok.application.port.out;

import com.sssok.domain.folder.Folder;
import java.util.Optional;

// 폴더 영속화 출력
public interface FolderRepository {

    Folder save(Folder folder);

    Optional<Folder> findByRoomIdAndName(Long roomId, String name);
}
