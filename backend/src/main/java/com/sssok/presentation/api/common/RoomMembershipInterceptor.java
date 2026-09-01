package com.sssok.presentation.api.common;

import com.sssok.application.auth.exception.UnauthorizedException;
import com.sssok.application.port.out.RoomMemberRepository;
import com.sssok.application.port.out.RoomRepository;
import com.sssok.application.port.out.TokenProvider;
import com.sssok.application.room.exception.NotRoomMemberException;
import com.sssok.application.room.exception.RoomExpiredException;
import com.sssok.application.room.exception.RoomNotFoundException;
import com.sssok.domain.room.Room;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Instant;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

// 폴더/미디어-폴더 API 공통 관문: roomId 경로에 물린 요청이 존재/유효한 방의, 입장한 사용자에게서
// 왔는지를 컨트롤러 진입 전에 확인한다. 통과하면 각 서비스는 폴더/미디어 자체의 규칙만 신경 쓰면 된다.
@Component
@RequiredArgsConstructor
public class RoomMembershipInterceptor implements HandlerInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";

    private final RoomRepository roomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final TokenProvider tokenProvider;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }

        Long roomId = extractRoomId(request);
        Long memberId = extractMemberId(request);

        Room room = roomRepository.findById(roomId)
            .orElseThrow(() -> new RoomNotFoundException(roomId));
        if (!room.canEnter(Instant.now())) {
            throw new RoomExpiredException();
        }
        roomMemberRepository.findByRoomIdAndMemberId(roomId, memberId)
            .orElseThrow(NotRoomMemberException::new);

        return true;
    }

    @SuppressWarnings("unchecked")
    private Long extractRoomId(HttpServletRequest request) {
        Map<String, String> pathVariables =
            (Map<String, String>) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        return Long.valueOf(pathVariables.get("roomId"));
    }

    private Long extractMemberId(HttpServletRequest request) {
        String authorizationHeader = request.getHeader("Authorization");
        if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            throw new UnauthorizedException("다시 접속해주세요");
        }
        return tokenProvider.parse(authorizationHeader.substring(BEARER_PREFIX.length()));
    }
}
