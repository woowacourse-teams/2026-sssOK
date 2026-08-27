package com.sssok.infrastructure.persistence.download;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DownloadJobMediaJpaRepository extends JpaRepository<DownloadJobMediaJpaEntity, Long> {

    List<DownloadJobMediaJpaEntity> findAllByDownloadJobId(Long downloadJobId);
}
