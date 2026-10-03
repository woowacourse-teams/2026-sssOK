package com.sssok.infrastructure.persistence.member;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface MemberJpaRepository extends JpaRepository<MemberJpaEntity, Long> {

    // 같은 회원의 요청끼리 줄을 세운다. 앞선 트랜잭션이 커밋할 때까지 기다린다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<MemberJpaEntity> findWithLockById(Long id);
}
