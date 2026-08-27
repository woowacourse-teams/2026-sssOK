package com.sssok.application.download;

import com.sssok.application.port.out.DownloadJobRepository;
import com.sssok.domain.download.DownloadJob;
import com.sssok.domain.file.StorageKey;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// DownloadCompressionWorker의 각 상태 전이를 별도 빈으로 뺀 이유: 압축 자체는 트랜잭션을 오래
// 붙들면 안 되는 긴 I/O라 워커 메서드 자체엔 @Transactional을 못 둔다. 그렇다고 워커 안에서
// this.markRunning() 처럼 같은 빈의 메서드를 호출하면 프록시를 안 거쳐 @Transactional이
// 조용히 무시된다(자기 호출 문제). 그래서 RoomPurger처럼 실제 트랜잭션이 필요한 단위를
// 별도 빈으로 분리해, 워커가 프록시를 거쳐 호출하게 한다.
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

    // 진행률은 파일 하나를 다 쓸 때마다 별도 트랜잭션으로 즉시 커밋한다 — 압축 전체가 끝날
    // 때까지 한 트랜잭션에 몰아두면, 상태 조회(GetDownloadJobStatusService)가 그 사이엔
    // progress 변화를 전혀 보지 못한다.
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
