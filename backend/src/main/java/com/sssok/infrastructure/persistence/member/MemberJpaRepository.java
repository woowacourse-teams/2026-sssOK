package com.sssok.infrastructure.persistence.member;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberJpaRepository extends JpaRepository<MemberJpaEntity, Long> {
}
