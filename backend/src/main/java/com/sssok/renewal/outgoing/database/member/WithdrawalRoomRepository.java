package com.sssok.renewal.outgoing.database.member;

import com.sssok.infrastructure.persistence.room.RoomJpaRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class WithdrawalRoomRepository {

    private final RoomJpaRepository roomJpaRepository;

    public List<Long> findUndeletedHostedRoomIds(Long memberId) {
        return roomJpaRepository.findUndeletedIdsByHostId(memberId);
    }
}
