package com.sssok.application.media;

// payload 대신 id 만 넘긴다. 커밋 전에 만든 payload 는 그 사이 담은 폴더를 놓쳐,
// 뒤늦게 도착한 media.ready 가 방금 담은 폴더를 화면에서 지운다.
public record MediaReadyEvent(Long roomId, Long mediaId) {
}
