package com.sssok.infrastructure.persistence.file;

import com.sssok.application.port.out.FileRepository;
import com.sssok.domain.file.StoredFile;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// findAllByRoomId/deleteAllByRoomId: stored_file은 존재 확인용 최소 매핑(id만)이라
// 실제 업로드 파이프라인(#16)이 StoredFile을 온전히 채워 넣을 때까지는 지울 대상이 없다.
@Component
@RequiredArgsConstructor
public class FileRepositoryAdapter implements FileRepository {

    private final StoredFileJpaRepository jpaRepository;

    @Override
    public List<Long> findExistingIds(List<Long> ids) {
        return jpaRepository.findAllById(ids).stream()
            .map(StoredFileJpaEntity::getId)
            .toList();
    }

    @Override
    public List<StoredFile> findAllByRoomId(Long roomId) {
        return List.of();
    }

    @Override
    public void deleteAllByRoomId(Long roomId) {
    }
}
