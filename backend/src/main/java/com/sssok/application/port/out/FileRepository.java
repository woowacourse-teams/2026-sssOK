package com.sssok.application.port.out;

import com.sssok.domain.file.StoredFile;
import java.util.List;
import java.util.Optional;

// 파일 영속화 출력
public interface FileRepository {

    StoredFile save(StoredFile storedFile);

    List<StoredFile> saveAll(List<StoredFile> storedFiles);

    Optional<StoredFile> findById(Long id);

    List<StoredFile> findAllByIdIn(List<Long> ids);

    // 담기/꺼내기에서 어떤 mediaId가 실제로 존재하는지 확인하는 용도.
    // 넘긴 id 중 존재하는 것만 골라 반환한다 — 나머지는 notFoundMediaIds로 취급하면 된다.
    List<Long> findExistingIds(List<Long> ids);

    List<StoredFile> findAllByRoomId(Long roomId);

    void deleteAllByRoomId(Long roomId);
}
