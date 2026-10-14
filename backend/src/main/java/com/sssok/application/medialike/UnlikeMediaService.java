package com.sssok.application.medialike;

import com.sssok.application.port.out.MediaLikeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UnlikeMediaService {

    private final LikeTargets likeTargets;
    private final MediaLikeRepository mediaLikeRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public MediaLikeResult unlike(Long roomId, Long mediaId, Long memberId) {
        likeTargets.requireVisibleInRoom(roomId, mediaId);

        if (mediaLikeRepository.unlike(mediaId, memberId)) {
            eventPublisher.publishEvent(new MediaLikeChangedEvent(roomId, mediaId));
        }
        return new MediaLikeResult(mediaId, false, mediaLikeRepository.countByMediaId(mediaId));
    }
}
