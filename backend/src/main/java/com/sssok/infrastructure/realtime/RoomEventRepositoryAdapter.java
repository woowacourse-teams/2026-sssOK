package com.sssok.infrastructure.realtime;

import com.sssok.application.port.out.RoomEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RoomEventRepositoryAdapter implements RoomEventRepository {

    private final RoomEventJpaRepository jpaRepository;

    @Override
    public void deleteAllByRoomId(Long roomId) {
        jpaRepository.deleteAllByRoomId(roomId);
    }
}