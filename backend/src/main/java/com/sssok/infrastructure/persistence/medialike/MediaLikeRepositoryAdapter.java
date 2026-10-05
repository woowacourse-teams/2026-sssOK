package com.sssok.infrastructure.persistence.medialike;

import com.sssok.application.port.out.MediaLikeRepository;
import com.sssok.domain.file.UploadStatus;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MediaLikeRepositoryAdapter implements MediaLikeRepository {

    private final MediaLikeJpaRepository jpaRepository;

    @Override
    public boolean likeIfStatusIn(Long mediaId, Long memberId, Collection<UploadStatus> statuses) {
        if (statuses.isEmpty()) {
            return false;
        }
        List<String> statusNames = statuses.stream().map(UploadStatus::name).toList();
        return jpaRepository.insertIfAbsentAndStatusIn(mediaId, memberId, statusNames) > 0;
    }

    @Override
    public boolean unlike(Long mediaId, Long memberId) {
        return jpaRepository.deleteIfPresent(mediaId, memberId) > 0;
    }

    @Override
    public long countByMediaId(Long mediaId) {
        return jpaRepository.countByMediaId(mediaId);
    }

    @Override
    public Map<Long, Long> countByMediaIds(List<Long> mediaIds) {
        if (mediaIds.isEmpty()) {
            return Map.of();
        }
        return jpaRepository.countGroupByMediaIdIn(mediaIds).stream()
            .collect(Collectors.toMap(
                row -> (Long) row[0],
                row -> ((Number) row[1]).longValue()));
    }

    @Override
    public Set<Long> findLikedMediaIds(Long memberId, List<Long> mediaIds) {
        if (mediaIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(jpaRepository.findMediaIdsByMemberIdAndMediaIdIn(memberId, mediaIds));
    }

    @Override
    public void deleteAllByMediaIds(List<Long> mediaIds) {
        if (mediaIds.isEmpty()) {
            return;
        }
        jpaRepository.deleteByMediaIdIn(mediaIds);
    }
}
