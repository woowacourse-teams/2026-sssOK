package com.sssok.application.media;

import com.sssok.domain.file.StoredFile;
import com.sssok.domain.file.UploadStatus;
import java.time.Instant;
import java.util.List;

// 미디어 목록 API 가 생기면 같은 형태를 쓰도록 응용 계층에 둔다.
public record MediaDetail(Long mediaId, String type, String fileName, String mimeType, long size,
                          String thumbnailUrl, String originalUrl, Integer width, Integer height,
                          Integer duration, List<Long> folderIds, Long uploaderId,
                          String uploaderName, String status, Instant uploadedAt) {

    // 서명 URL 을 그대로 싣지 않고 이 경로를 준다. 서명은 몇 분이면 만료되어, 목록을 캐싱하거나
    // 잠시 뒤에 그리면 깨진다. 경로는 만료되지 않고, 방 참여자만 통과한다.
    private static final String THUMBNAIL_PATH = "/api/v1/rooms/%d/media/%d/thumbnail";
    private static final String ORIGINAL_PATH = "/api/v1/rooms/%d/media/%d/original";

    // 썸네일·크기는 워커가 채우기 전까지, 영상은 끝까지 비어 있다.
    // duration 은 영상 길이라, 스트리밍으로 읽는 도구가 붙기 전까지 비어 있다.
    public static MediaDetail of(StoredFile file, String uploaderName, List<Long> folderIds) {
        return new MediaDetail(
            file.getId(),
            file.getMediaType().isImage() ? "IMAGE" : "VIDEO",
            file.getOriginalFileName(),
            file.getMediaType().contentType(),
            file.getFileSize().bytes(),
            thumbnailUrl(file),
            originalUrl(file),
            file.getWidth(),
            file.getHeight(),
            null,
            folderIds,
            file.getUploaderId(),
            uploaderName,
            file.getStatus().name(),
            file.getCreatedAt()
        );
    }

    private static String thumbnailUrl(StoredFile file) {
        if (file.getThumbnailKey() == null) {
            return null;
        }
        return THUMBNAIL_PATH.formatted(file.getRoomId(), file.getId());
    }

    // 워커가 손대는 중(PROCESSING)이면 원본이 바뀌는 중일 수 있어, 그동안은 주지 않는다.
    private static String originalUrl(StoredFile file) {
        if (file.getStatus() != UploadStatus.READY) {
            return null;
        }
        return ORIGINAL_PATH.formatted(file.getRoomId(), file.getId());
    }
}
