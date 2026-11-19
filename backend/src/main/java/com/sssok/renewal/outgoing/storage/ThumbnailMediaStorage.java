package com.sssok.renewal.outgoing.storage;

import com.sssok.domain.file.DerivativeContentType;
import com.sssok.domain.file.StorageKey;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 썸네일 처리에 필요한 원본 읽기와 파생본 저장. 영상 원본은 메모리에 올리지 않는다. */
@Component
@RequiredArgsConstructor
public class ThumbnailMediaStorage {
    private final FileStorage storage;

    public byte[] download(StorageKey key) {
        try (InputStream stream = storage.openDownloadStream(key)) {
            return stream.readAllBytes();
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    public void uploadThumbnail(StorageKey key, byte[] content) {
        upload(key, content);
    }

    public void uploadPreview(StorageKey key, byte[] content) {
        upload(key, content);
    }

    private void upload(StorageKey key, byte[] content) {
        storage.upload(key, content, DerivativeContentType.of(key));
    }
}
