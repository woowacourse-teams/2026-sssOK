package com.sssok.application.room;

import com.sssok.application.port.out.RoomRepository;
import com.sssok.domain.room.Room;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

// 만료 시각이 지났는데 저장된 상태가 아직 ACTIVE 인 방을 EXPIRED 로 넘긴다.
// 응답과 입장 판단은 이미 시각까지 보고 정하므로(Room#statusAt), 이 작업은 저장값을 실제 상태에 맞추는 일이다.
@Slf4j
@Service
@RequiredArgsConstructor
public class ExpireRoomsService {

    private final RoomRepository roomRepository;

    public int expire(Instant now) {
        List<Room> targets = roomRepository.findAllExpiredActive(now);

        int expired = 0;
        for (Room room : targets) {
            // 방마다 따로 저장한다. 그 사이 방장이 방을 지워 버전이 어긋나면 그 방만 건너뛰고,
            // 아직 ACTIVE 로 남았다면 다음 회차에 다시 대상으로 잡힌다.
            try {
                room.expire();
                roomRepository.save(room);
                expired++;
            } catch (RuntimeException e) {
                log.warn("방 {} 만료 처리에 실패했습니다. 다음 회차에 다시 시도합니다", room.getId(), e);
            }
        }
        return expired;
    }
}
