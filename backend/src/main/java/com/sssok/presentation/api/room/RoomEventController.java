package com.sssok.presentation.api.room;

import com.sssok.application.auth.exception.UnauthorizedException;
import com.sssok.application.port.out.TokenProvider;
import com.sssok.application.room.SubscribeRoomEventsService;
import com.sssok.infrastructure.realtime.InMemorySseEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/rooms")
@RequiredArgsConstructor
public class RoomEventController {

    private static final String BEARER_PREFIX = "Bearer ";

    private final TokenProvider tokenProvider;
    private final SubscribeRoomEventsService subscribeRoomEventsService;
    private final InMemorySseEventPublisher sseEventPublisher;

    @GetMapping(value = "/{roomId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(
        @PathVariable Long roomId,
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @RequestParam(value = "token", required = false) String tokenParam,
        @RequestHeader(value = "Last-Event-ID", required = false) Long lastEventId
    ) {
        // EventSource는 커스텀 헤더를 못 붙이므로, Authorization 헤더가 없으면 쿼리 파라미터로 대체 인증한다.
        tokenProvider.parse(resolveToken(authorizationHeader, tokenParam));
        subscribeRoomEventsService.validate(roomId);
        return sseEventPublisher.subscribe(roomId, lastEventId);
    }

    private String resolveToken(String authorizationHeader, String tokenParam) {
        if (authorizationHeader != null && authorizationHeader.startsWith(BEARER_PREFIX)) {
            return authorizationHeader.substring(BEARER_PREFIX.length());
        }
        if (tokenParam != null && !tokenParam.isBlank()) {
            return tokenParam;
        }
        throw new UnauthorizedException("다시 접속해주세요");
    }
}
