package com.sssok.renewal.outgoing.database;

import com.sssok.renewal.domain.notification.DeviceToken;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class DeviceTokenRepository {
    private final DeviceTokenJpaRepository jpaRepository;

    @Transactional
    public void save(DeviceToken deviceToken, Instant now) {
        jpaRepository.upsert(deviceToken.getMemberId(), deviceToken.getToken(),
            deviceToken.getPlatform().name(), now);
    }

    @Transactional
    public void delete(Long memberId, String token) {
        jpaRepository.deleteByMemberIdAndToken(memberId, token);
    }

    // 만료된 토큰은 누구의 것이든 더는 쓸 수 없어 회원과 상관없이 지운다.
    @Transactional
    public void deleteExpired(String token) {
        jpaRepository.deleteByToken(token);
    }

    public List<DeviceToken> findRecipients(Long roomId, Long actorId) {
        return jpaRepository.findRecipients(roomId, actorId).stream()
            .map(DeviceTokenJpaEntity::toDomain)
            .toList();
    }
}
