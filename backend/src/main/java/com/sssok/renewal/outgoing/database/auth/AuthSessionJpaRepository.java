package com.sssok.renewal.outgoing.database.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuthSessionJpaRepository extends JpaRepository<AuthSessionJpaEntity, String> {
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update AuthSessionJpaEntity s set s.revoked = true where s.memberId = :memberId")
    int revokeAll(@Param("memberId") Long memberId);
}
