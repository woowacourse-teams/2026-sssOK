package com.sssok.infrastructure.persistence.file;

import com.sssok.application.port.out.FileRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

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
}
