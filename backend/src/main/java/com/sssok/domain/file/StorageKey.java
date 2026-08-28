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

    // 원본 키에서 썸네일 키를 만든다. 같은 이름을 thumbnails/ 아래에 두면
    // 원본과 짝이 눈에 보이고, 접두사만으로 썸네일 전체를 골라낼 수 있다.
    public StorageKey thumbnail() {
        int lastSlash = value.lastIndexOf('/');
        return new StorageKey(
                value.substring(0, lastSlash + 1) + "thumbnails/" + value.substring(lastSlash + 1));
    }
}
