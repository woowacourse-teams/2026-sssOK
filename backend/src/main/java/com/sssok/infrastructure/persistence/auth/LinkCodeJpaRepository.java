package com.sssok.infrastructure.persistence.auth;

import java.util.Collection;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LinkCodeJpaRepository extends JpaRepository<LinkCodeJpaEntity, Long> {

    void deleteAllByMemberId(Long memberId);

    @Modifying
    @Query("DELETE FROM LinkCodeJpaEntity e WHERE e.memberId IN :memberIds")
    void deleteAllByMemberIdIn(@Param("memberIds") Collection<Long> memberIds);

    Optional<LinkCodeJpaEntity> findByCode(String code);

    @Modifying
    @Query("DELETE FROM LinkCodeJpaEntity e WHERE e.code = :code")
    int deleteByCode(@Param("code") String code);
}
