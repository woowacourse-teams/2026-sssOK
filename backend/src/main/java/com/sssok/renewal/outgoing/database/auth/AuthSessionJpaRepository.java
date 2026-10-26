package com.sssok.renewal.outgoing.database.auth;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuthSessionJpaRepository extends JpaRepository<AuthSessionJpaEntity, String> {
    @Query("select s.memberId from AuthSessionJpaEntity s where s.id = :id")
    Optional<Long> findMemberId(@Param("id") String id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update AuthSessionJpaEntity s set s.revoked = true where s.memberId = :memberId")
    int revokeAll(@Param("memberId") Long memberId);
}
