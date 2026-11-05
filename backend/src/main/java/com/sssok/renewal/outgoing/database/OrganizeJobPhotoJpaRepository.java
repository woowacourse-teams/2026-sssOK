package com.sssok.renewal.outgoing.database;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizeJobPhotoJpaRepository extends JpaRepository<OrganizeJobPhotoJpaEntity, Long> {

    List<OrganizeJobPhotoJpaEntity> findAllByJobIdOrderByMediaId(Long jobId);
}
