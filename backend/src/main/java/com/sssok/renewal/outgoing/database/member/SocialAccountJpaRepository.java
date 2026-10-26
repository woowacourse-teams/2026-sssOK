package com.sssok.renewal.outgoing.database.member;

import com.sssok.renewal.domain.member.SocialProvider;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SocialAccountJpaRepository extends JpaRepository<SocialAccountJpaEntity, Long> {
    Optional<SocialAccountJpaEntity> findByProviderAndProviderUserId(SocialProvider provider, String providerUserId);

    List<SocialAccountJpaEntity> findByMemberId(Long memberId);

    @Modifying(flushAutomatically = true)
    @Query("delete from SocialAccountJpaEntity a where a.id = :id and a.memberId = :memberId")
    int deleteByIdAndMemberId(@Param("id") Long id, @Param("memberId") Long memberId);
}
