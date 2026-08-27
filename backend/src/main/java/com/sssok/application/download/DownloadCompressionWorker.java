package com.sssok.application.download;

import com.sssok.application.port.out.DownloadJobRepository;
import com.sssok.domain.download.DownloadJob;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// @Async 자체는 여기 두지 않는다 — 이 클래스를 직접 호출해도 항상 동기로 동작해야
// 단위 테스트에서 Spring의 비동기 프록시 없이 그대로 검증할 수 있다. 실제 비동기 진입점은
// 이 워커를 호출하는 DownloadJobEventListener 쪽 @Async 다.
@Service
@RequiredArgsConstructor
public class DownloadCompressionWorker {

    private final DownloadJobRepository downloadJobRepository;

    // TODO(#84): 실제 zip 압축·업로드(openDownloadStream/openUploadStream)는 다음 단위에서 이어붙인다.
    // 지금은 QUEUED -> RUNNING 전이만 수행해 "이벤트 발행 -> 비동기 픽업" 배선이 실제로 도는지 검증한다.
    @Transactional
    public void compress(Long jobId) {
        DownloadJob job = downloadJobRepository.findById(jobId)
            .orElseThrow(() -> new IllegalStateException("다운로드 잡을 찾을 수 없습니다: " + jobId));
        job.markRunning();
        downloadJobRepository.save(job);
    }
}
