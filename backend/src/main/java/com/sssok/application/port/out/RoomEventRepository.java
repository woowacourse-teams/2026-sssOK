package com.sssok.application.port.out;

// 방 이벤트 기록 영속화 출력
public interface RoomEventRepository {

    // 방을 영구 삭제할 때 그 방의 이벤트 기록도 함께 지운다.
    void deleteAllByRoomId(Long roomId);
}