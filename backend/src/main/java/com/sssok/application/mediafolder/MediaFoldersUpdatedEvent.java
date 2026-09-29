package com.sssok.application.mediafolder;

import java.util.List;

// SSE로 발행되는 "media.folders.updated" 이벤트 payload.
// action으로 담기(ADD)/꺼내기(REMOVE)를 구분하고, 응답과 같은 folders[] 구성을 그대로 실어
// 클라이언트가 폴더 칩의 개수 표시를 갱신할 수 있게 한다.
// 다만 담기·꺼내기에서만 발행된다 — 폴더를 지정해 올린 미디어가 완료 등록되는 시점에는
// 이 이벤트가 아니라 media.created 가 나가고, 그 payload 의 folderIds 로 갱신한다.
public record MediaFoldersUpdatedEvent(Long roomId, String action, List<Long> mediaIds, List<FolderSummary> folders) {

    public static MediaFoldersUpdatedEvent added(Long roomId, List<Long> mediaIds, List<FolderSummary> folders) {
        return new MediaFoldersUpdatedEvent(roomId, "ADD", mediaIds, folders);
    }

    public static MediaFoldersUpdatedEvent removed(Long roomId, List<Long> mediaIds, List<FolderSummary> folders) {
        return new MediaFoldersUpdatedEvent(roomId, "REMOVE", mediaIds, folders);
    }
}
