package com.sssok.application.media;

import com.sssok.application.port.out.FileStoragePort;
import com.sssok.domain.file.StoredFile;
import com.sssok.domain.file.UploadStatus;
import com.sssok.infrastructure.config.DownloadProperties;
import com.sssok.infrastructure.config.ThumbnailProperties;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// 목록/단건 조회, 업로드 등록 직후 응답, 썸네일 완료 SSE 이벤트가 모두 같은 규칙으로
// 썸네일·원본 서명 URL을 만들어야 해서 한 곳에 모은다. 서명은 로컬 연산이라 네트워크 왕복이 없다.
@Component
@RequiredArgsConstructor
public class MediaUrlResolver {

    private final FileStoragePort fileStoragePort;
    private final ThumbnailProperties thumbnailProperties;
    private final DownloadProperties downloadProperties;

    public MediaUrls resolve(StoredFile file) {
        Instant now = Instant.now();
        String thumbnailUrl = null;
        Instant thumbnailUrlExpiresAt = null;
        // 워커가 아직 만들지 않았거나(PROCESSING) 영상이라 만들 수 없으면 비운다.
        if (file.getThumbnailKey() != null) {
            thumbnailUrl = fileStoragePort.presignGet(file.getThumbnailKey(), "inline",
                file.getMediaType().contentType(), thumbnailProperties.displayUrlTtl());
            thumbnailUrlExpiresAt = now.plus(thumbnailProperties.displayUrlTtl());
        }

        String originalUrl = null;
        Instant originalUrlExpiresAt = null;
        // 워커가 손대는 중(PROCESSING)이면 원본이 바뀌는 중일 수 있어, READY가 아니면 비운다.
        if (file.getStatus() == UploadStatus.READY) {
            originalUrl = fileStoragePort.presignGet(file.getStorageKey(), "inline",
                file.getMediaType().contentType(), downloadProperties.presignedGetTtl());
            originalUrlExpiresAt = now.plus(downloadProperties.presignedGetTtl());
        }

        return new MediaUrls(thumbnailUrl, thumbnailUrlExpiresAt, originalUrl, originalUrlExpiresAt);
    }
}
