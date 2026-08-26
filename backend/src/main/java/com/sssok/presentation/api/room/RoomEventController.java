package com.sssok.presentation.api.room;

import com.sssok.application.port.out.EventSubscriberPort;
import com.sssok.application.room.SubscribeRoomEventsService;
import com.sssok.presentation.auth.AuthMember;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Tag(name = "방 실시간 이벤트(SSE)", description = "방에서 일어나는 변화(입장/수정/삭제 등)를 실시간으로 받는 Server-Sent Events 구독")
@RestController
@RequestMapping("/rooms")
@RequiredArgsConstructor
public class RoomEventController {

    private final SubscribeRoomEventsService subscribeRoomEventsService;
    private final EventSubscriberPort sseEventPublisher;

    @Operation(
        summary = "방 이벤트 구독",
        description = """
            이 방에서 일어나는 이벤트(room.member.joined / room.updated / room.deleted 등)를 \
            실시간으로 스트리밍 받는다. 연결은 서버가 끊기 전까지 계속 열려 있다(text/event-stream).

            인증: 브라우저 EventSource는 커스텀 헤더를 못 붙이므로, Authorization 헤더 대신 \
            ?token={accessToken} 쿼리 파라미터로도 인증할 수 있다(이 API에서만 허용). \
            방에 입장하지 않은 회원이 구독을 시도하면 403이 난다.

            재연결: 연결이 끊겼다가 다시 구독할 때 Last-Event-ID 헤더에 마지막으로 받은 \
            이벤트 id를 실어 보내면, 그 사이 놓친 이벤트를 순서대로 재전송받는다.

            사용법: new EventSource(`/api/v1/rooms/${roomId}/events?token=${accessToken}`) \
            로 연결하고, addEventListener로 이벤트 타입별 콜백을 등록해서 사용한다.\
            """
    )
    @GetMapping(value = "/{roomId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(
        @Parameter(hidden = true) @AuthMember(allowQueryToken = true) Long memberId,
        @Parameter(description = "구독할 방의 roomId") @PathVariable Long roomId,
        @Parameter(description = "재연결 시, 마지막으로 받은 이벤트의 id. 그 이후 놓친 이벤트를 재전송받는다")
        @RequestHeader(value = "Last-Event-ID", required = false) Long lastEventId
    ) {
        subscribeRoomEventsService.validate(roomId, memberId);
        return sseEventPublisher.subscribe(roomId, lastEventId);
    }
}
