package com.sssok.application.folder;

import com.sssok.application.port.out.EventPublisherPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// 폴더 생성·이름변경·삭제 서비스가 발행한 도메인 이벤트를, 트랜잭션이 실제로 커밋된 뒤에만 SSE로 내보낸다.
// 중복 이름으로 막힌 요청처럼 롤백되는 경우엔 커밋이 없으므로 이벤트도 나가지 않는다.
// REQUIRES_NEW인 이유는 RoomEventListener와 같다 — AFTER_COMMIT 시점엔 원래 트랜잭션이 끝나 있어서
// room_events 저장을 위한 새 트랜잭션을 명시적으로 열어야 한다.
@Component
@RequiredArgsConstructor
public class FolderEventListener {

    private final EventPublisherPort eventPublisher;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(FolderCreatedEvent event) {
        eventPublisher.publish(event.roomId(), "folder.created", event);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(FolderRenamedEvent event) {
        eventPublisher.publish(event.roomId(), "folder.renamed", event);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(FolderDeletedEvent event) {
        eventPublisher.publish(event.roomId(), "folder.deleted", event);
    }
}