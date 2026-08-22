package com.sssok.infrastructure.persistence.auth;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LinkCodeJpaRepository extends JpaRepository<LinkCodeJpaEntity, Long> {

    void deleteAllByMemberId(Long memberId);

    Optional<LinkCodeJpaEntity> findByCode(String code);

    void deleteByCode(String code);
}
