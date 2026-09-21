package com.sssok.infrastructure.persistence.admin;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminJpaRepository extends JpaRepository<AdminJpaEntity, Long> {

    Optional<AdminJpaEntity> findByLoginId(String loginId);

    boolean existsByLoginId(String loginId);

    List<AdminJpaEntity> findAllByOrderByIdAsc();

    long countByRole(String role);
}
