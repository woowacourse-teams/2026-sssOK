package com.sssok.domain.folder;

import lombok.Getter;

// 방 안에서 파일을 묶는 폴더. 지금 요구사항은 폴더 안에 폴더가 없는 1depth 구조라,
// 재귀 구조 없이 "존재한다는 사실"과 "이름"만 책임진다.
// Room과는 서로 다른 애그리거트이며 roomId(ID)로만 참조한다.
@Getter
public class Folder {

    private final Long id;
    private final Long roomId;
    private FolderName name;

    private Folder(Long id, Long roomId, FolderName name) {
        this.id = id;
        this.roomId = roomId;
        this.name = name;
    }

    public static Folder create(Long roomId, FolderName name) {
        return new Folder(null, roomId, name);
    }

    public static Folder reconstruct(Long id, Long roomId, FolderName name) {
        return new Folder(id, roomId, name);
    }

    public void rename(FolderName newName) {
        this.name = newName;
    }

    public boolean belongsTo(Long roomId) {
        return this.roomId.equals(roomId);
    }
}
