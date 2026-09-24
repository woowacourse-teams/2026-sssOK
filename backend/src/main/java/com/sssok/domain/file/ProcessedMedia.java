package com.sssok.domain.file;

import java.time.Instant;

// 워커가 원본을 들여다보고 알아낸 것들. 하나라도 빠질 수 있어 전부 비어 있을 수 있다.
public record ProcessedMedia(StorageKey thumbnailKey, StorageKey previewKey,
                             Integer width, Integer height,
                             Integer durationSeconds, Instant takenAt, GeoPoint location) {

    // 썸네일을 만들지 못한 경우. 원본은 멀쩡하므로 FAILED 가 아니라 썸네일 없이 완료로 넘긴다.
    public static ProcessedMedia none() {
        return new ProcessedMedia(null, null, null, null, null, null, null);
    }

    // 사진. 재생시간이 없고, 대신 EXIF 에서 촬영 시각과 좌표가 나온다.
    // previewKey 는 애니메이션 GIF 처럼 프리뷰를 만들지 않는 경우 비어 있다.
    public static ProcessedMedia ofImage(StorageKey thumbnailKey, StorageKey previewKey,
                                         Integer width, Integer height,
                                         Instant takenAt, GeoPoint location) {
        return new ProcessedMedia(thumbnailKey, previewKey, width, height, null, takenAt, location);
    }

    // 영상. 재생시간이 더 붙을 뿐, 촬영 시각과 좌표는 사진과 같은 자리에 담는다 —
    // 상세 화면이 사진인지 영상인지 가리지 않고 같은 필드를 읽는다.
    // 영상은 프리뷰를 만들지 않는다 — 상세 화면이 재생하는 것은 원본이고, 그 사이에 끼울
    // 중간 해상도 정지 이미지가 필요 없다.
    public static ProcessedMedia ofVideo(StorageKey thumbnailKey, Integer width, Integer height,
                                         Integer durationSeconds, Instant takenAt,
                                         GeoPoint location) {
        return new ProcessedMedia(thumbnailKey, null, width, height, durationSeconds, takenAt,
                location);
    }
}