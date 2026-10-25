package com.sssok.application.room;

import com.sssok.application.port.out.EventPublisherPort;
import com.sssok.application.port.out.EventSubscriberPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// 방 상태 변경 서비스가 발행한 도메인 이벤트를, 트랜잭션이 실제로 커밋된 뒤에만 SSE로 내보낸다.
// 커밋 전에 emitter.send()까지 끝내버리면 이후 커밋이 실패해 롤백돼도 클라이언트는 이미 변경을 받은 상태가 된다.
// room_events를 저장하는 리스너만 REQUIRES_NEW를 사용한다. 연결 종료는 DB를 쓰지 않는다.
@Component
@RequiredArgsConstructor
public class RoomEventListener {

    private final EventPublisherPort eventPublisher;
    private final EventSubscriberPort eventSubscriber;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(RoomMemberLeftEvent event) {
        eventSubscriber.disconnectMembership(event.roomId(), event.membershipId());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(RoomMemberJoinedEvent event) {
        eventPublisher.publish(event.roomId(), "room.member.joined", event);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(RoomUpdatedEvent event) {
        eventPublisher.publish(event.roomId(), "room.updated", event);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(RoomDeletedEvent event) {
        eventPublisher.publish(event.roomId(), "room.deleted", event);
    }
}
