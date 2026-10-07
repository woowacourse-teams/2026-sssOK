package com.sssok.application.medialike;

import com.sssok.application.media.exception.MediaNotFoundException;
import com.sssok.application.port.out.MediaLikeRepository;
import com.sssok.domain.file.UploadStatus;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LikeMediaService {

    private final LikeTargets likeTargets;
    private final MediaLikeRepository mediaLikeRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public MediaLikeResult like(Long roomId, Long mediaId, Long memberId) {
        likeTargets.requireVisibleInRoom(roomId, mediaId);

        boolean added = mediaLikeRepository.likeIfStatusIn(mediaId, memberId, UploadStatus.visibleStatuses());
        if (added) {
            eventPublisher.publishEvent(new MediaLikeChangedEvent(roomId, mediaId));
        } else {
            requireAlreadyLiked(mediaId, memberId);
        }
        return new MediaLikeResult(mediaId, true, mediaLikeRepository.countByMediaId(mediaId));
    }

    // 이미 누른 게 아니라면 확인 뒤 FAILED 로 바뀌었거나 지워진 미디어다.
    private void requireAlreadyLiked(Long mediaId, Long memberId) {
        if (mediaLikeRepository.findLikedMediaIds(memberId, List.of(mediaId)).isEmpty()) {
            throw new MediaNotFoundException();
        }
    }
}
