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
    //
    // 확장자는 원본이 아니라 파생본 포맷을 따른다. 내용은 WebP 인데 키가 .jpg 로 끝나면
    // 스토리지를 직접 들여다볼 때 무엇이 들었는지 알 수 없다.
    public StorageKey thumbnail(String extension) {
        return derive("thumbnails/", extension);
    }

    // 원본 확장자를 그대로 쓰는 기존 경로. 파생본 포맷을 설정에서 받도록 옮기는 후속 커밋에서 지운다.
    public StorageKey thumbnail() {
        int lastSlash = value.lastIndexOf('/');
        return new StorageKey(
                value.substring(0, lastSlash + 1) + "thumbnails/" + value.substring(lastSlash + 1));
    }

    // 상세 모달용 파생본. 썸네일과 같은 규칙으로 previews/ 아래에 둔다.
    public StorageKey preview(String extension) {
        return derive("previews/", extension);
    }

    private StorageKey derive(String prefix, String extension) {
        int lastSlash = value.lastIndexOf('/');
        String fileName = value.substring(lastSlash + 1);
        int lastDot = fileName.lastIndexOf('.');
        String baseName = lastDot < 0 ? fileName : fileName.substring(0, lastDot);
        return new StorageKey(
                value.substring(0, lastSlash + 1) + prefix + baseName + "." + extension);
    }
}
