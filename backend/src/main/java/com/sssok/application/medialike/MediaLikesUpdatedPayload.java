package com.sssok.application.medialike;

public record MediaLikesUpdatedPayload(Long roomId, Long mediaId, long likeCount) {
}
