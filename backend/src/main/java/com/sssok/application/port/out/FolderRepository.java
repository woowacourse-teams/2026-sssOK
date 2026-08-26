package com.sssok.application.port.out;

import com.sssok.domain.folder.Folder;
import java.util.List;
import java.util.Optional;

// 폴더 영속화 출력
public interface FolderRepository {

    Folder save(Folder folder);

    Optional<Folder> findById(Long id);

    // 순서·중복 보장 없음 — 존재하는 것만 돌아오므로, 요청한 id와 개수를 비교해 없는 id를 가려내는 용도.
    List<Folder> findAllById(List<Long> ids);

    Optional<Folder> findByRoomIdAndName(Long roomId, String name);

    void deleteById(Long id);

    void deleteAllByRoomId(Long roomId);
}
