package com.sssok.application.media;

import com.sssok.application.port.out.EventPublisherPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// 등록 트랜잭션이 실제로 커밋된 뒤에만 SSE 로 내보낸다.
// 커밋 전에 보내면 이후 롤백돼도 클라이언트는 이미 사진이 올라간 것으로 알고 목록에 그린다.
@Component
@RequiredArgsConstructor
public class MediaEventListener {

    private final EventPublisherPort eventPublisher;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(MediaCreatedEvent event) {
        eventPublisher.publish(event.roomId(), "media.created", event.media());
    }

    // 썸네일이 붙어 목록에 실제로 그릴 수 있게 된 시점. 프론트는 media.created 로 자리를 잡아두고
    // 이 이벤트로 같은 mediaId 의 항목을 갈아끼운다.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(MediaReadyEvent event) {
        eventPublisher.publish(event.roomId(), "media.ready", event.media());
    }
}
