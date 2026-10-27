package com.sssok.renewal.outgoing.database;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class RoomNotificationSettingRepository {
    private final RoomNotificationSettingJpaRepository jpaRepository;

    // 설정을 바꾼 적이 없으면 켜진 것으로 본다.
    public boolean isEnabled(Long roomId, Long memberId) {
        return jpaRepository.findEnabled(roomId, memberId).orElse(true);
    }

    @Transactional
    public void save(Long roomId, Long memberId, boolean enabled, Instant now) {
        jpaRepository.upsert(roomId, memberId, enabled, now);
    }
}
