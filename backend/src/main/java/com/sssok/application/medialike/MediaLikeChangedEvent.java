package com.sssok.application.medialike;

// 좋아요가 실제로 추가·취소됐다는 신호. 개수는 싣지 않는다 — 여러 사람이 동시에 누르면 각 트랜잭션은
// 서로의 좋아요를 보지 못해 커밋 전에 센 값이 실제보다 작다. 개수는 커밋 뒤 리스너가 다시 센다.
public record MediaLikeChangedEvent(Long roomId, Long mediaId) {
}
