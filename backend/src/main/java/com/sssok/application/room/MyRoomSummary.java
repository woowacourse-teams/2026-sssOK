package com.sssok.application.room;

public record MyRoomSummary(
    Long roomId,
    String code,
    String name,
    boolean isHost,
    long mediaCount,
    long memberCount,
    String thumbnailUrl
) {
}
