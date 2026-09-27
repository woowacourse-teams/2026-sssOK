package com.sssok.application.folder;

import com.sssok.domain.folder.Folder;

// SSE로 발행되는 "folder.renamed" 이벤트 payload. 바뀐 뒤의 이름을 담는다.
public record FolderRenamedEvent(Long roomId, Long folderId, String name) {

    public static FolderRenamedEvent from(Folder folder) {
        return new FolderRenamedEvent(folder.getRoomId(), folder.getId(), folder.getName().value());
    }
}