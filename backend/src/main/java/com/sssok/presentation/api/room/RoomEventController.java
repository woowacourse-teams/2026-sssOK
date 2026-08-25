package com.sssok.presentation.api.room;

import com.sssok.application.room.SubscribeRoomEventsService;
import com.sssok.infrastructure.realtime.InMemorySseEventPublisher;
import com.sssok.presentation.auth.AuthMember;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/rooms")
@RequiredArgsConstructor
public class RoomEventController {

    private final SubscribeRoomEventsService subscribeRoomEventsService;
    private final InMemorySseEventPublisher sseEventPublisher;

    @GetMapping(value = "/{roomId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(
        @AuthMember Long memberId,
        @PathVariable Long roomId,
        @RequestHeader(value = "Last-Event-ID", required = false) Long lastEventId
    ) {
        subscribeRoomEventsService.validate(roomId, memberId);
        return sseEventPublisher.subscribe(roomId, lastEventId);
    }
}
