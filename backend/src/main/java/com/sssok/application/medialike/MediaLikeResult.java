package com.sssok.application.medialike;

// 좋아요 추가·취소 뒤의 상태. liked 는 요청한 사람 기준이고, likeCount 는 그 시점의 전체 수다.
public record MediaLikeResult(Long mediaId, boolean liked, long likeCount) {
}
