package com.sssok.application.port.out;

// 폴더 영속화 출력
public interface FolderRepository {

    void deleteAllByRoomId(Long roomId);
}
