package com.sssok.application.mediafolder;

import com.sssok.application.port.out.EventPublisherPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// 담기/꺼내기 서비스가 발행한 도메인 이벤트를, 트랜잭션이 실제로 커밋된 뒤에만 SSE로 내보낸다.
@Component
@RequiredArgsConstructor
public class MediaFolderEventListener {

    private final EventPublisherPort eventPublisher;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(MediaFoldersUpdatedEvent event) {
        eventPublisher.publish(event.roomId(), "media.folders.updated", event);
    }
}
