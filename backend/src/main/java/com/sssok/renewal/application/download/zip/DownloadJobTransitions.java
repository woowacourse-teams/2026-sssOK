package com.sssok.renewal.application.download.zip;

import com.sssok.renewal.outgoing.database.DownloadJobRepository;
import com.sssok.renewal.domain.download.DownloadJob;
import com.sssok.domain.file.StorageKey;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// 워커 안에서 직접 부르면 자기 호출이라 @Transactional 이 무시돼 별도 빈으로 뺐다.
@Component
@RequiredArgsConstructor
class DownloadJobTransitions {

    private final DownloadJobRepository downloadJobRepository;

    @Transactional
    DownloadJob markRunning(Long jobId) {
        DownloadJob job = get(jobId);
        job.markRunning();
        return downloadJobRepository.save(job);
    }

    // 압축 도중에도 상태 조회에 보이도록 바뀔 때마다 따로 커밋한다.
    @Transactional
    void updateProgress(Long jobId, int progress) {
        DownloadJob job = get(jobId);
        job.updateProgress(progress);
        downloadJobRepository.save(job);
    }

    @Transactional
    void markReady(Long jobId, StorageKey zipStorageKey) {
        DownloadJob job = get(jobId);
        job.markReady(zipStorageKey, Instant.now());
        downloadJobRepository.save(job);
    }

    @Transactional
    void markFailed(Long jobId, String reason) {
        DownloadJob job = get(jobId);
        job.markFailed(reason);
        downloadJobRepository.save(job);
    }

    private DownloadJob get(Long jobId) {
        return downloadJobRepository.findById(jobId)
            .orElseThrow(() -> new IllegalStateException("다운로드 잡을 찾을 수 없습니다: " + jobId));
    }
}
