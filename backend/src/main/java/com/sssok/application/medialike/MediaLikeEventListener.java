package com.sssok.application.medialike;

import com.sssok.application.port.out.EventPublisherPort;
import com.sssok.application.port.out.FileRepository;
import com.sssok.application.port.out.MediaLikeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class MediaLikeEventListener {

    private final EventPublisherPort eventPublisher;
    private final FileRepository fileRepository;
    private final MediaLikeRepository mediaLikeRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(MediaLikeChangedEvent event) {
        if (fileRepository.findById(event.mediaId()).isEmpty()) {
            return;
        }
        long likeCount = mediaLikeRepository.countByMediaId(event.mediaId());
        eventPublisher.publish(event.roomId(), "media.likes.updated",
            new MediaLikesUpdatedPayload(event.roomId(), event.mediaId(), likeCount));
    }
}
