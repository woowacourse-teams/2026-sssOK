package com.sssok.domain.file;

import com.sssok.domain.file.exception.InvalidStorageKeyException;

import java.util.UUID;

public record StorageKey(String value) {

    public StorageKey {
        if (value == null || value.isBlank()) {
            throw new InvalidStorageKeyException("스토리지 키는 비어 있을 수 없습니다.");
        }
    }

    public static StorageKey generate(Long roomId, MediaType mediaType) {
        return new StorageKey("rooms/%d/%s.%s".formatted(
                roomId, UUID.randomUUID(), mediaType.extension()));
    }
}
