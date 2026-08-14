package com.sssok.application.port.out;

// Room 컨텍스트(담당 A)가 Member/File 컨텍스트(담당 B)에 노출하는 조회 전용 포트.
// B는 이 인터페이스에만 의존하고, Room 내부 구현은 몰라도 된다.
// 구현체는 이 이슈 범위가 아니며, RoomRepository를 사용해 이후에 채운다.
public interface RoomPermissionPort {

    boolean isHost(Long roomId, Long memberId);

    boolean canUpload(Long roomId, Long memberId);

    boolean isRoomUsable(Long roomId);

    // zip 다운로드 파일명(ShareDrop_방코드.zip)에 쓰인다.
    String getRoomCode(Long roomId);
}
