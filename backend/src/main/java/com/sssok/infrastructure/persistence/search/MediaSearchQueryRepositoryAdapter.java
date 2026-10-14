package com.sssok.infrastructure.persistence.search;

import com.sssok.application.port.out.MediaSearchQueryRepository;
import java.util.List;
import com.sssok.application.media.MediaUploaderFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class MediaSearchQueryRepositoryAdapter implements MediaSearchQueryRepository {
    private final MediaSearchDocumentJpaRepository jpaRepository;

    @Override
    @Transactional(readOnly = true)
    public List<Match> search(Long roomId, String vector, String model, int dimensions, double threshold,
        Long folderId, Long requesterId, MediaUploaderFilter uploader) {
        return jpaRepository.search(roomId, vector, model, dimensions, threshold, folderId, requesterId, uploader.name()).stream()
            .map(row -> new Match(row.getMediaId(), row.getSimilarity())).toList();
    }
}
