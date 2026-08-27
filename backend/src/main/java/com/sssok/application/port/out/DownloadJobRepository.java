package com.sssok.application.port.out;

import com.sssok.domain.download.DownloadJob;
import com.sssok.domain.download.DownloadJobStatus;
import java.util.List;
import java.util.Optional;

// 다운로드 잡 영속화 출력
public interface DownloadJobRepository {

    DownloadJob save(DownloadJob job);

    Optional<DownloadJob> findById(Long id);

    // 동시 진행 중인 잡 수 제한(429)에 쓴다.
    long countByRequesterIdAndStatusIn(Long requesterId, List<DownloadJobStatus> statuses);

    // 잡 생성 시점에 압축 대상 media id를 그대로 저장해둔다 — 워커가 나중에 다시
    // 리졸빙하지 않고 이 목록을 그대로 압축하게 하기 위함.
    void saveJobMedia(Long jobId, List<Long> mediaIds);

    // 이 잡이 압축해야 할 media id 목록. 워커가 실제로 무엇을 zip에 담을지 결정할 때 쓴다.
    List<Long> findMediaIdsByJobId(Long jobId);
}
