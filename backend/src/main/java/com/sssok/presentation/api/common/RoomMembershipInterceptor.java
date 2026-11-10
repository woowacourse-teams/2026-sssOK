package com.sssok.presentation.api.common;

import com.sssok.renewal.application.auth.exception.UnauthorizedException;
import com.sssok.application.port.out.RoomMemberRepository;
import com.sssok.application.port.out.RoomRepository;
import com.sssok.renewal.incoming.auth.VerifiedAuthentication;
import com.sssok.renewal.outgoing.security.TokenProvider;
import com.sssok.application.room.exception.NotRoomMemberException;
import com.sssok.application.room.exception.RoomExpiredException;
import com.sssok.application.room.exception.RoomNotFoundException;
import com.sssok.common.exception.InvalidRequestParameterException;
import com.sssok.domain.room.Room;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

// 폴더/미디어-폴더 API 공통 관문: roomId 경로에 물린 요청이 존재/유효한 방의, 입장한 사용자에게서
// 왔는지를 컨트롤러 진입 전에 확인한다. 통과하면 각 서비스는 폴더/미디어 자체의 규칙만 신경 쓰면 된다.
@Component
@RequiredArgsConstructor
public class RoomMembershipInterceptor implements HandlerInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String ROOM_ID = "roomId";

    private final RoomRepository roomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final TokenProvider tokenProvider;

    // 이 메서드는 스프링 프록시를 거쳐 읽기 트랜잭션 하나로 실행된다(preHandle 이 끝나면 함께 끝난다).
    // 토큰 확인(회원·세션)과 방·참여자 확인이 각자 커넥션을 빌리면 컨트롤러 진입 전에만 4~6번을 빌린다.
    // tokenProvider.parse 도 읽기 트랜잭션이라 여기에 합류해 같은 커넥션을 쓴다.
    @Override
    @Transactional(readOnly = true)
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }

        Long roomId = extractRoomId(request);
        String token = extractToken(request);

        Long memberId = tokenProvider.parse(token);
        Room room = roomRepository.findById(roomId)
            .orElseThrow(() -> new RoomNotFoundException(roomId));
        if (!room.canEnter()) {
            throw new RoomExpiredException();
        }
        roomMemberRepository.findByRoomIdAndMemberId(roomId, memberId)
            .orElseThrow(NotRoomMemberException::new);
        // 컨트롤러의 @AuthMember 가 같은 토큰을 다시 검증하지 않도록 결과를 남긴다.
        VerifiedAuthentication.remember(request, token, memberId);

        return true;
    }

    // 여기서 터진 숫자 변환 오류를 그대로 흘려보내면 전역 핸들러가 내부 결함까지 400 으로 덮는다.
    // 요청 값이 잘못됐다는 사실을 아는 이 지점에서 전용 예외로 바꿔 던진다.
    @SuppressWarnings("unchecked")
    private Long extractRoomId(HttpServletRequest request) {
        Map<String, String> pathVariables =
            (Map<String, String>) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        String roomId = pathVariables == null ? null : pathVariables.get(ROOM_ID);
        if (roomId == null) {
            throw new InvalidRequestParameterException(ROOM_ID);
        }
        try {
            return Long.valueOf(roomId);
        } catch (NumberFormatException e) {
            throw new InvalidRequestParameterException(ROOM_ID);
        }
    }

    private String extractToken(HttpServletRequest request) {
        String authorizationHeader = request.getHeader("Authorization");
        if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            throw new UnauthorizedException("다시 접속해주세요");
        }
        return authorizationHeader.substring(BEARER_PREFIX.length());
    }
}
