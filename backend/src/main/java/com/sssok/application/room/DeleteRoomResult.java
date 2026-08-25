package com.sssok.application.room;

import java.time.Instant;

public record DeleteRoomResult(Instant deletedAt, Instant purgeAt) {
}
