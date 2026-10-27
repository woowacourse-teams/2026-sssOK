package com.sssok.presentation.api.room;

import com.sssok.application.room.MyRoomSummary;
import java.util.List;

public record MyRoomsResponse(List<MyRoomSummary> rooms) {
}
