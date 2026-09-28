package com.sssok.application.media;

import com.sssok.application.port.out.FileStoragePort;
import com.sssok.domain.file.StoredFile;
import com.sssok.infrastructure.config.ThumbnailProperties;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// 목록·단건·업로드 응답·SSE가 같은 URL 계약을 쓰도록 한 곳에서 조립한다.
// 외부 응답은 preview/original 구분 없이 최종적으로 displayUrl 하나만 노출한다.
@Component
@RequiredArgsConstructor
public class MediaUrlResolver {

    private final FileStoragePort fileStoragePort;
    private final ThumbnailProperties thumbnailProperties;

    public MediaUrls resolve(StoredFile file) {
        Instant now = Instant.now();
        MediaUrlSource.ResolvedUrl thumbnail = MediaUrlSource.thumbnail(
            file.getThumbnailKey(), thumbnailProperties.displayUrlTtl())
            .resolve(fileStoragePort, now);
        MediaUrlSource.ResolvedUrl display = MediaUrlSource.display(
            file, thumbnailProperties.displayUrlTtl())
            .resolve(fileStoragePort, now);

        return new MediaUrls(
            thumbnail.url(), thumbnail.expiresAt(),
            display.url(), display.expiresAt());
    }
}
