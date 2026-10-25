package com.sssok.application.media;

import com.sssok.domain.file.StorageKey;
import com.sssok.domain.file.StoredFile;
import com.sssok.infrastructure.config.ThumbnailProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// 목록·단건·업로드 응답·SSE가 같은 URL 계약을 쓰도록 한 곳에서 조립한다.
// 외부 응답은 preview/original 구분 없이 최종적으로 displayUrl 하나만 노출한다.
@Component
@RequiredArgsConstructor
public class MediaUrlResolver {

    private final MediaReadUrlProvider urlProvider;
    private final ThumbnailProperties thumbnailProperties;

    public String resolveThumbnailUrl(String thumbnailKey) {
        StorageKey key = thumbnailKey == null ? null : new StorageKey(thumbnailKey);
        return MediaUrlSource.thumbnail(key, thumbnailProperties.displayUrlTtl())
            .resolve(urlProvider)
            .url();
    }

    public MediaUrls resolve(StoredFile file) {
        MediaUrlSource.ResolvedUrl thumbnail = MediaUrlSource.thumbnail(
            file.getThumbnailKey(), thumbnailProperties.displayUrlTtl())
            .resolve(urlProvider);
        MediaUrlSource.ResolvedUrl display = MediaUrlSource.display(
            file, thumbnailProperties.displayUrlTtl())
            .resolve(urlProvider);

        return new MediaUrls(
            thumbnail.url(), thumbnail.expiresAt(),
            display.url(), display.expiresAt());
    }
}
