package com.sssok.renewal.outgoing.database.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface RefreshTokenJpaRepository extends JpaRepository<RefreshTokenJpaEntity, String> {
    @Query("select t.sessionId from RefreshTokenJpaEntity t where t.tokenHash = :hash")
    Optional<String> findSessionId(@Param("hash") String hash);
}
