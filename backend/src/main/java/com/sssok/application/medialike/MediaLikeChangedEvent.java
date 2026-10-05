package com.sssok.application.medialike;

// 동시에 여러 명이 누르면 커밋 전에 센 값이 틀리므로 개수는 커밋 뒤 리스너가 센다.
public record MediaLikeChangedEvent(Long roomId, Long mediaId) {
}
