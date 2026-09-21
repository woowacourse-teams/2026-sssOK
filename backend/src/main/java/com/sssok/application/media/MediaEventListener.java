package com.sssok.application.media;

import com.sssok.application.port.out.EventPublisherPort;
import com.sssok.application.port.out.FileRepository;
import java.util.List;
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
    private final FileRepository fileRepository;
    private final MediaDetailAssembler mediaDetailAssembler;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(MediaCreatedEvent event) {
        eventPublisher.publish(event.roomId(), "media.created", event.media());
    }

    // 썸네일이 붙어 목록에 실제로 그릴 수 있게 된 시점. 프론트는 media.created 로 자리를 잡아두고
    // 이 이벤트로 같은 mediaId 의 항목을 통째로 갈아끼운다. payload 를 커밋 뒤에 다시 읽어 만드는
    // 이유는 MediaReadyEvent 참고. 그 사이 지워졌다면 행이 없어 아무것도 내보내지 않는다.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(MediaReadyEvent event) {
        fileRepository.findById(event.mediaId())
            .map(file -> mediaDetailAssembler.assemble(List.of(file)).get(0))
            .ifPresent(media -> eventPublisher.publish(event.roomId(), "media.ready", media));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(MediaDeletedEvent event) {
        eventPublisher.publish(event.roomId(), "media.deleted", event);
    }
}
