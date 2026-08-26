package com.sssok.domain.folder;

import com.sssok.domain.folder.exception.FolderNameTooLongException;
import com.sssok.domain.folder.exception.InvalidFolderNameException;

// 폴더 이름 값 객체. 앞뒤 공백을 제거한 뒤 1~12자여야 한다.
public record FolderName(String value) {

    private static final int MAX_LENGTH = 12;

    public FolderName {
        if (value == null || value.isBlank()) {
            throw new InvalidFolderNameException();
        }
        value = value.strip();
        if (value.isEmpty()) {
            throw new InvalidFolderNameException();
        }
        if (value.length() > MAX_LENGTH) {
            throw new FolderNameTooLongException();
        }
    }
}
