package com.sssok.application.download;

import com.sssok.application.port.out.DownloadJobRepository;
import com.sssok.application.port.out.FileStoragePort;
import com.sssok.domain.download.DownloadJob;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// 실물을 먼저 지운다. DB 행이 남아 있어야 중간에 실패해도 다음 회차에 다시 찾아 시도할 수 있다
// (RoomPurger와 같은 이유).
@Component
@RequiredArgsConstructor
public class DownloadJobExpirer {

    private final DownloadJobRepository downloadJobRepository;
    private final FileStoragePort fileStoragePort;

    @Transactional
    public void expire(DownloadJob job) {
        fileStoragePort.delete(job.getZipStorageKey());
        job.markExpired();
        downloadJobRepository.save(job);
    }
}
