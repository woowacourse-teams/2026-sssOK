package com.sssok.application.folder;

import com.sssok.domain.folder.Folder;
import java.time.Instant;

// SSE로 발행되는 "folder.created" 이벤트 payload.
// 다른 참여자가 목록을 다시 받아오지 않고도 폴더 칩을 그릴 수 있도록 생성 응답과 같은 필드를 담는다.
public record FolderCreatedEvent(Long roomId, Long folderId, String name, Instant createdAt) {

    public static FolderCreatedEvent from(Folder folder) {
        return new FolderCreatedEvent(
            folder.getRoomId(),
            folder.getId(),
            folder.getName().value(),
            folder.getCreatedAt()
        );
    }
}