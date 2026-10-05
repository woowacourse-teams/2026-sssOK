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

// 좋아요 추가·취소가 실제로 커밋된 뒤에만 SSE로 내보낸다. 개수를 여기서 다시 세는 이유는
// MediaLikeChangedEvent 참고. 그 사이 미디어가 지워졌다면 media.deleted 가 나가므로 아무것도 내보내지 않는다.
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
