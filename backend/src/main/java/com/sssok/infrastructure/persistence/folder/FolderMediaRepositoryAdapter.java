package com.sssok.infrastructure.persistence.folder;

import com.sssok.application.port.out.FolderMediaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FolderMediaRepositoryAdapter implements FolderMediaRepository {

    private final FolderMediaJpaRepository jpaRepository;

    @Override
    public long detachAllFromFolder(Long folderId) {
        return jpaRepository.deleteByFolderId(folderId);
    }

    @Override
    public long detachAllByRoomId(Long roomId) {
        return jpaRepository.deleteByRoomId(roomId);
    }
}
