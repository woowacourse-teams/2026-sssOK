package com.sssok.application.medialike;

// SSE로 발행되는 "media.likes.updated" 이벤트 payload.
// 방 전체에 한 번 뿌리므로 보는 사람마다 다른 likedByMe 는 싣지 않는다.
public record MediaLikesUpdatedPayload(Long roomId, Long mediaId, long likeCount) {
}
