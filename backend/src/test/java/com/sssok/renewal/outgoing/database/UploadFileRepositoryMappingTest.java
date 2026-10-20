package com.sssok.renewal.outgoing.database;

import com.sssok.infrastructure.persistence.file.StoredFileJpaRepository;
import com.sssok.infrastructure.persistence.file.FileRepositoryAdapter;
import java.util.List;
import org.mockito.Mockito;
import org.mockito.ArgumentMatchers;
import com.sssok.support.UploadSizePolicyFixture;

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
        assertRoundTrip(original);
    }

    @Test
    void 아직_파생본과_메타데이터가_없는_파일도_양쪽_저장소에서_보존된다() {
        StoredFile original = StoredFile.reserve(20L, 30L, "a.jpg", "image/jpeg", new FileSize(1024),
            Instant.parse("2026-10-19T00:00:00Z"), UploadSizePolicyFixture.SIZE_POLICY);
        assertRoundTrip(original);
    }

    private void assertRoundTrip(StoredFile original) {
        StoredFileJpaRepository jpaRepository =
            Mockito.mock(StoredFileJpaRepository.class);
        Mockito.when(jpaRepository.save(ArgumentMatchers.any()))
            .thenAnswer(invocation -> invocation.getArgument(0));
        Mockito.when(jpaRepository.saveAll(ArgumentMatchers.any()))
            .thenAnswer(invocation -> invocation.getArgument(0));
        FileRepositoryAdapter legacyRepository =
            new FileRepositoryAdapter(jpaRepository);
        assertThat(legacyRepository.save(original)).usingRecursiveComparison().isEqualTo(original);
        assertThat(legacyRepository.saveAll(List.of(original)).getFirst())
            .usingRecursiveComparison().isEqualTo(original);
        UploadFileRepository repository = new UploadFileRepository(jpaRepository);
        assertThat(repository.savePendingUploads(List.of(original)).getFirst())
            .usingRecursiveComparison().isEqualTo(original);
        assertThat(repository.save(original))
            .usingRecursiveComparison().isEqualTo(original);
    }
}
