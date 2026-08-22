package com.sssok.infrastructure.persistence.auth;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LinkCodeJpaRepository extends JpaRepository<LinkCodeJpaEntity, Long> {

    void deleteAllByMemberId(Long memberId);
}
