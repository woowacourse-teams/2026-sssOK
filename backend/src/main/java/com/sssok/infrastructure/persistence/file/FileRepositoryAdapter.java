package com.sssok.infrastructure.persistence.file;

import com.sssok.application.port.out.FileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FileRepositoryAdapter implements FileRepository {

    private final StoredFileJpaRepository jpaRepository;

    @Override
    public boolean existsById(Long id) {
        return jpaRepository.existsById(id);
    }
}
