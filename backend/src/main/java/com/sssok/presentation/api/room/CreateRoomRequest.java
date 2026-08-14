package com.sssok.presentation.api.room;

// 형식 검증(필수값 여부 등)은 RoomName 값 객체가 담당한다 (도메인 규칙을 한곳에 모아둠).
public record CreateRoomRequest(String name) {
}
