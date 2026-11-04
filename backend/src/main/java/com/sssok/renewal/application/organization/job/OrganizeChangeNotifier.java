package com.sssok.renewal.application.organization.job;

import com.sssok.renewal.outgoing.database.OrganizeJobRepository;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

// 작업을 바꾸는 트랜잭션 안에서 부른다. 방 점유가 바뀌었으면 같은 트랜잭션에서 방 버전을 올리고,
// 알림은 커밋된 뒤에만 나간다. 커밋과 알림 사이에 유실돼도 재연결 때 GET current 의 스냅샷과 버전으로 바로잡는다.
@Component
@RequiredArgsConstructor
public class OrganizeChangeNotifier {
    public enum Occupancy {
        UNCHANGED,
        BUSY,
        FREE
    }

    private final OrganizeJobRepository repository;
    private final ApplicationEventPublisher eventPublisher;
    @Qualifier("organizeClock")
    private final Clock clock;

    public void changed(Long jobPk, Long roomId, Occupancy occupancy) {
        if (occupancy == Occupancy.UNCHANGED) {
            eventPublisher.publishEvent(new OrganizeJobChangedEvent(jobPk, roomId, null, false));
            return;
        }
        OrganizeJobRepository.RoomState state =
            repository.bumpRoomState(roomId, occupancy == Occupancy.BUSY, clock.instant());
        eventPublisher.publishEvent(new OrganizeJobChangedEvent(jobPk, roomId, state.version(), state.busy()));
    }
}
