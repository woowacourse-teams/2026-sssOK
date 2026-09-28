package com.sssok.application.media;

import com.sssok.application.port.out.FileStoragePort;
import com.sssok.domain.file.DerivativeContentType;
import com.sssok.domain.file.StorageKey;
import com.sssok.domain.file.StoredFile;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

sealed interface MediaUrlSource {

    ResolvedUrl resolve(FileStoragePort storage, Instant now);

    static MediaUrlSource thumbnail(StorageKey key, Duration ttl) {
        return Optional.ofNullable(key)
            .<MediaUrlSource>map(found -> new Derivative(found, ttl))
            .orElse(Unavailable.INSTANCE);
    }

    static MediaUrlSource display(StoredFile file, Duration previewTtl, Duration originalTtl) {
        return Optional.ofNullable(file.getPreviewKey())
            .<MediaUrlSource>map(key -> new Derivative(key, previewTtl))
            .orElseGet(() -> Original.of(file, originalTtl));
    }

    record Derivative(StorageKey key, Duration ttl) implements MediaUrlSource {

        @Override
        public ResolvedUrl resolve(FileStoragePort storage, Instant now) {
            String url = storage.presignGet(
                key, "inline", DerivativeContentType.of(key), ttl);
            return new ResolvedUrl(url, now.plus(ttl));
        }
    }

    record Original(StoredFile file, Duration ttl) implements MediaUrlSource {

        static MediaUrlSource of(StoredFile file, Duration ttl) {
            return file.getStatus().isDownloadable()
                ? new Original(file, ttl)
                : Unavailable.INSTANCE;
        }

        @Override
        public ResolvedUrl resolve(FileStoragePort storage, Instant now) {
            String url = storage.presignGet(file.getStorageKey(), "inline",
                file.getMediaType().contentType(), ttl);
            return new ResolvedUrl(url, now.plus(ttl));
        }
    }

    enum Unavailable implements MediaUrlSource {
        INSTANCE;

        @Override
        public ResolvedUrl resolve(FileStoragePort storage, Instant now) {
            return ResolvedUrl.NONE;
        }
    }

    record ResolvedUrl(String url, Instant expiresAt) {
        static final ResolvedUrl NONE = new ResolvedUrl(null, null);
    }
}
