package com.sssok.application.download;

// 압축 잡이 생성돼 QUEUED 상태로 저장됐음을 알린다. 트랜잭션 커밋 후에만 워커가 픽업해야
// 커밋되지 않은(존재하지 않을 수도 있는) 잡을 워커가 먼저 읽어버리는 경합을 막을 수 있다.
public record DownloadJobRequestedEvent(Long jobId) {
}
