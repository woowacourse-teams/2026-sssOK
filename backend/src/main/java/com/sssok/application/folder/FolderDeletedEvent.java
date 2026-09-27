package com.sssok.application.folder;

// SSE로 발행되는 "folder.deleted" 이벤트 payload.
// 폴더에 담겨 있던 사진은 지워지지 않고 루트로 돌아가므로, 사진 쪽 이벤트는 따로 발행하지 않는다.
public record FolderDeletedEvent(Long roomId, Long folderId) {
}