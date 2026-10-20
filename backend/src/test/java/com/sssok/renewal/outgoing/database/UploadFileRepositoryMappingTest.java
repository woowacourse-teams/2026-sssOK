package com.sssok.renewal.outgoing.database;

import static org.assertj.core.api.Assertions.assertThat;

import com.sssok.domain.file.*;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class UploadFileRepositoryMappingTest {
    @Test
    void 도메인과_엔티티를_왕복해도_전체_필드가_보존된다() {
        Instant now = Instant.parse("2026-10-19T00:00:00Z");
        StoredFile original = StoredFile.reconstruct(10L, 20L, 30L, "a.jpg", MediaType.JPEG,
            new FileSize(1024), new StorageKey("rooms/20/a.jpg"), 40L, UploadStatus.READY,
            now, now.plusSeconds(1), 2, new StorageKey("thumb/a.jpg"), new StorageKey("preview/a.jpg"),
            100, 200, 3, now.minusSeconds(60), new GeoPoint(new BigDecimal("37.123456"), new BigDecimal("127.123456")));
        com.sssok.infrastructure.persistence.file.StoredFileJpaRepository jpaRepository =
            org.mockito.Mockito.mock(com.sssok.infrastructure.persistence.file.StoredFileJpaRepository.class);
        org.mockito.Mockito.when(jpaRepository.save(org.mockito.ArgumentMatchers.any()))
            .thenAnswer(invocation -> invocation.getArgument(0));
        UploadFileRepository repository = new UploadFileRepository(jpaRepository);
        assertThat(repository.save(original))
            .usingRecursiveComparison().isEqualTo(original);
    }
}
