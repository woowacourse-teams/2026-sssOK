package com.sssok.application.mediafolder;

import com.sssok.domain.folder.Folder;

// 담기/꺼내기 응답과 SSE 이벤트에 함께 실리는 "변경 후 폴더 상태" 조각.
public record FolderSummary(Long id, String name, int photoCount) {

    public static FolderSummary of(Folder folder, long photoCount) {
        return new FolderSummary(folder.getId(), folder.getName().value(), (int) photoCount);
    }
}
