package com.sssok.application.folder.exception;

public class FolderNotFoundException extends RuntimeException {

    public FolderNotFoundException(Long folderId) {
        super("존재하지 않는 폴더입니다: " + folderId);
    }
}
