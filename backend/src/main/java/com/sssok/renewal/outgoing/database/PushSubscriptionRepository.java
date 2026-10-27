package com.sssok.renewal.outgoing.database;

import com.sssok.renewal.domain.notification.PushSubscription;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class PushSubscriptionRepository {
    private final PushSubscriptionJpaRepository jpaRepository;

    @Transactional
    public void save(PushSubscription subscription, Instant now) {
        jpaRepository.upsert(subscription.getMemberId(), subscription.getEndpoint(),
            subscription.getP256dh(), subscription.getAuth(), now);
    }

    @Transactional
    public void delete(Long memberId, String endpoint) {
        jpaRepository.deleteByMemberIdAndEndpoint(memberId, endpoint);
    }

    // 만료된 구독은 누구의 것이든 더는 쓸 수 없어 회원과 상관없이 지운다.
    @Transactional
    public void deleteExpired(String endpoint) {
        jpaRepository.deleteByEndpoint(endpoint);
    }

    public List<PushSubscription> findRecipients(Long roomId, Long actorId) {
        return jpaRepository.findRecipients(roomId, actorId).stream()
            .map(PushSubscriptionJpaEntity::toDomain)
            .toList();
    }
}
