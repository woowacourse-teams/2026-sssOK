package com.sssok.application.mediafolder;

import java.util.List;

// SSE로 발행되는 "media.folders.updated" 이벤트 payload.
// action으로 담기(ADD)/꺼내기(REMOVE)를 구분하고, 응답과 같은 folders[] 구성을 그대로 실어
// 클라이언트가 이 이벤트만으로 폴더 칩의 개수 표시를 갱신할 수 있게 한다.
public record MediaFoldersUpdatedEvent(Long roomId, String action, List<Long> mediaIds, List<FolderSummary> folders) {

    public static MediaFoldersUpdatedEvent added(Long roomId, List<Long> mediaIds, List<FolderSummary> folders) {
        return new MediaFoldersUpdatedEvent(roomId, "ADD", mediaIds, folders);
    }

    public static MediaFoldersUpdatedEvent removed(Long roomId, List<Long> mediaIds, List<FolderSummary> folders) {
        return new MediaFoldersUpdatedEvent(roomId, "REMOVE", mediaIds, folders);
    }
}
