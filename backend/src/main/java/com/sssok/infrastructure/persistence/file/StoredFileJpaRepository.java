package com.sssok.infrastructure.persistence.file;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StoredFileJpaRepository extends JpaRepository<StoredFileJpaEntity, Long> {
}
