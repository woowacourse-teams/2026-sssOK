package com.sssok.domain.folder;

import com.sssok.domain.folder.exception.InvalidFolderNameException;
// 폴더 이름 값 객체.
public record FolderName(String value) {

    private static final int MAX_LENGTH = 20;

    public FolderName {
        if (value == null || value.isBlank()) {
            throw new InvalidFolderNameException(value);
        }
        if (value.length() > MAX_LENGTH) {
            throw new InvalidFolderNameException(value);
        }
    }
}
