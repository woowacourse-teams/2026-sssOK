package com.sssok.application.room;

import com.sssok.application.port.out.RoomRepository;
import com.sssok.domain.room.RetentionPolicy;
import com.sssok.domain.room.Room;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

// 보존 기간이 지난 방을 실물과 DB에서 완전히 지운다.
// 상태를 PURGED 로 바꾸는 대신 행을 지우므로, 상태 머신의 purge 전이는 타지 않는다.
@Slf4j
@Service
@RequiredArgsConstructor
public class PurgeRoomService {

    private final RoomRepository roomRepository;
    private final RoomPurger roomPurger;

    private final RetentionPolicy retentionPolicy = new RetentionPolicy();

    public int purgeAll(Instant now) {
        List<Room> targets = roomRepository.findAllPurgeTargets(retentionPolicy.threshold(now));

        int purged = 0;
        for (Room room : targets) {
            // 한 방이 실패해도 나머지는 계속 지운다. 실패한 방은 다음 회차에 다시 대상으로 잡힌다.
            try {
                roomPurger.purge(room);
                purged++;
            } catch (RuntimeException e) {
                log.warn("방 {} 정리에 실패했습니다. 다음 회차에 다시 시도합니다", room.getId(), e);
            }
        }
        return purged;
    }
}
