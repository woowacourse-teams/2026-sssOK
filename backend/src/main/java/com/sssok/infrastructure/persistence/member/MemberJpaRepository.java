package com.sssok.infrastructure.persistence.member;

import java.util.Collection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemberJpaRepository extends JpaRepository<MemberJpaEntity, Long> {

    @Modifying
    @Query("DELETE FROM MemberJpaEntity m WHERE m.id IN :memberIds")
    void deleteAllByIdIn(@Param("memberIds") Collection<Long> memberIds);
}