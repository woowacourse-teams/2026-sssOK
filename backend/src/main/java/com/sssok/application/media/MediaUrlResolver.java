package com.sssok.application.media;

import com.sssok.application.port.out.FileStoragePort;
import com.sssok.domain.file.DerivativeContentType;
import com.sssok.domain.file.StorageKey;
import com.sssok.domain.file.StoredFile;
import com.sssok.infrastructure.config.DownloadProperties;
import com.sssok.infrastructure.config.ThumbnailProperties;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// 목록/단건 조회, 업로드 등록 직후 응답, 썸네일 완료 SSE 이벤트가 모두 같은 규칙으로 서명 URL 을
// 만들어야 해서 한 곳에 모은다. 서명은 로컬 연산이라 네트워크 왕복이 없다.
@Component
@RequiredArgsConstructor
public class MediaUrlResolver {

    private final FileStoragePort fileStoragePort;
    private final ThumbnailProperties thumbnailProperties;
    private final DownloadProperties downloadProperties;

    // 목록에는 타일에 그릴 썸네일만 싣는다. 여기에 프리뷰까지 얹으면 30장짜리 한 페이지가
    // 5MB 를 넘고, 그중 사용자가 실제로 여는 것은 한두 장이다.
    public MediaUrls forList(StoredFile file) {
        Instant now = Instant.now();
        return new MediaUrls(
            presignDerivative(file.getThumbnailKey(), thumbnailProperties.displayUrlTtl()),
            expiresAt(file.getThumbnailKey(), now, thumbnailProperties.displayUrlTtl()),
            null, null, null, null);
    }

    // 상세 화면은 사진이면 프리뷰를, 영상이면 재생할 원본을 쓴다.
    //
    // 사진의 원본 URL 은 싣지 않는다. 1600px 프리뷰면 모달에서 확대해도 충분하고, 저장 목적의
    // 다운로드는 다운로드 API 가 따로 맡는다.
    //
    // 다만 프리뷰가 없는 사진에는 원본을 그대로 내준다 — 이 기능이 생기기 전에 올라온 사진과
    // GIF 가 그렇다. 여기서 썸네일로 내려앉히면 400px 짜리가 전체 화면에 늘어나 눈에 띄게 흐리다.
    public MediaUrls forDetail(StoredFile file) {
        Instant now = Instant.now();
        Duration displayTtl = thumbnailProperties.displayUrlTtl();
        StorageKey previewKey = file.getPreviewKey();
        boolean needsOriginal = previewKey == null || file.getMediaType().isVideo();

        return new MediaUrls(
            presignDerivative(file.getThumbnailKey(), displayTtl),
            expiresAt(file.getThumbnailKey(), now, displayTtl),
            presignDerivative(previewKey, displayTtl),
            expiresAt(previewKey, now, displayTtl),
            needsOriginal ? presignOriginal(file) : null,
            needsOriginal ? expiresAtIf(file.getStatus().isDownloadable(), now,
                downloadProperties.presignedGetTtl()) : null);
    }

    // 업로드 등록 직후 응답과 media.created 이벤트용. 이 시점에는 아직 PROCESSING 이라 파생본이
    // 하나도 없으므로, 방금 올린 사진을 바로 보여주려면 원본 말고는 쓸 것이 없다.
    public MediaUrls forCreated(StoredFile file) {
        Instant now = Instant.now();
        Duration displayTtl = thumbnailProperties.displayUrlTtl();
        return new MediaUrls(
            presignDerivative(file.getThumbnailKey(), displayTtl),
            expiresAt(file.getThumbnailKey(), now, displayTtl),
            null, null,
            presignOriginal(file),
            expiresAtIf(file.getStatus().isDownloadable(), now,
                downloadProperties.presignedGetTtl()));
    }

    // 워커가 아직 만들지 않았거나(PROCESSING) 추출에 실패했으면 비운다.
    //
    // 서명에 쓰는 형식은 설정값이 아니라 키의 확장자에서 읽는다. 지금 설정을 보고 정하면,
    // 포맷을 바꾼 뒤 예전에 만들어 둔 파생본이 전부 틀린 Content-Type 으로 서명돼 깨진다.
    private String presignDerivative(StorageKey key, Duration ttl) {
        if (key == null) {
            return null;
        }
        return fileStoragePort.presignGet(key, "inline", DerivativeContentType.of(key), ttl);
    }

    // 실물이 없는 RESERVED·FAILED 에서만 비운다.
    private String presignOriginal(StoredFile file) {
        if (!file.getStatus().isDownloadable()) {
            return null;
        }
        return fileStoragePort.presignGet(file.getStorageKey(), "inline",
            file.getMediaType().contentType(), downloadProperties.presignedGetTtl());
    }

    private Instant expiresAt(StorageKey key, Instant now, Duration ttl) {
        return expiresAtIf(key != null, now, ttl);
    }

    private Instant expiresAtIf(boolean present, Instant now, Duration ttl) {
        return present ? now.plus(ttl) : null;
    }
}
