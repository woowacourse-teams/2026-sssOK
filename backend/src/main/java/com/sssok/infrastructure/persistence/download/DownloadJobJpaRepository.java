package com.sssok.infrastructure.persistence.download;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DownloadJobJpaRepository extends JpaRepository<DownloadJobJpaEntity, Long> {

    long countByRequesterIdAndStatusIn(Long requesterId, List<String> statuses);
}
