package com.sssok.domain.file;

import java.time.Instant;

// 워커가 원본을 들여다보고 알아낸 것들. 하나라도 빠질 수 있어 전부 비어 있을 수 있다.
public record ProcessedMedia(StorageKey thumbnailKey, Integer width, Integer height,
                             Instant takenAt, GeoPoint location) {

    // 영상처럼 들여다볼 도구가 없는 경우. 썸네일 없이 완료로 넘길 때 쓴다.
    public static ProcessedMedia none() {
        return new ProcessedMedia(null, null, null, null, null);
    }
}
