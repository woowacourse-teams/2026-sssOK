package com.sssok.renewal.outgoing.database.auth;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LegacyAuthRevocationRepository extends JpaRepository<LegacyAuthRevocationJpaEntity, Long> {
}
