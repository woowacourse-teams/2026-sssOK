package com.sssok.infrastructure.persistence.member;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface MemberJpaRepository extends JpaRepository<MemberJpaEntity, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<MemberJpaEntity> findWithLockById(Long id);
}
