package com.sssok.application.port.out;

import com.sssok.domain.file.UploadStatus;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface MediaLikeRepository {

    boolean likeIfStatusIn(Long mediaId, Long memberId, Collection<UploadStatus> statuses);

    boolean unlike(Long mediaId, Long memberId);

    long countByMediaId(Long mediaId);

    Map<Long, Long> countByMediaIds(List<Long> mediaIds);

    Set<Long> findLikedMediaIds(Long memberId, List<Long> mediaIds);

    void deleteAllByMediaIds(List<Long> mediaIds);
}
