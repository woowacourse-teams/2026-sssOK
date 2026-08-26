package com.sssok.presentation.api.room;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sssok.application.auth.exception.UnauthorizedException;
import com.sssok.application.port.out.EventSubscriberPort;
import com.sssok.application.port.out.RoomMemberRepository;
import com.sssok.application.port.out.RoomRepository;
import com.sssok.application.port.out.TokenProvider;
import com.sssok.application.room.SubscribeRoomEventsService;
import com.sssok.application.room.exception.RoomExpiredException;
import com.sssok.application.room.exception.RoomMembershipRequiredException;
import com.sssok.application.room.exception.RoomNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

// RoomEventController의 인증(헤더/쿼리 파라미터)·검증 실패 라우팅만 확인하는 슬라이스 테스트.
// 실제 구독·이벤트 전달은 API 인수 테스트가 담당한다.
@WebMvcTest(RoomEventController.class)
class RoomEventControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    TokenProvider tokenProvider;

    @MockitoBean
    SubscribeRoomEventsService subscribeRoomEventsService;

    @MockitoBean
    EventSubscriberPort sseEventPublisher;

    // WebConfig가 등록하는 RoomMembershipInterceptor가 요구하는 포트. 이 슬라이스의 경로는
    // 인터셉터 패턴과 무관하지만, 빈 자체는 컨텍스트에 함께 뜨므로 목으로 채워야 한다.
    @MockitoBean
    RoomRepository roomRepository;

    @MockitoBean
    RoomMemberRepository roomMemberRepository;

    @Test
    void Authorization_헤더로_구독하면_비동기_요청이_시작된다() throws Exception {
        given(tokenProvider.parse("valid-token")).willReturn(1L);
        given(sseEventPublisher.subscribe(1024L, null)).willReturn(new SseEmitter());

        mockMvc.perform(get("/api/v1/rooms/1024/events")
                .header("Authorization", "Bearer valid-token"))
            .andExpect(request().asyncStarted());
    }

    @Test
    void Last_Event_ID_헤더가_있으면_그대로_publisher에_전달된다() throws Exception {
        given(tokenProvider.parse("valid-token")).willReturn(1L);
        given(sseEventPublisher.subscribe(1024L, 42L)).willReturn(new SseEmitter());

        mockMvc.perform(get("/api/v1/rooms/1024/events")
                .header("Authorization", "Bearer valid-token")
                .header("Last-Event-ID", "42"))
            .andExpect(request().asyncStarted());

        verify(sseEventPublisher).subscribe(1024L, 42L);
    }

    @Test
    void Authorization_헤더가_없어도_token_쿼리_파라미터로_구독된다() throws Exception {
        given(tokenProvider.parse("query-token")).willReturn(1L);
        given(sseEventPublisher.subscribe(1024L, null)).willReturn(new SseEmitter());

        mockMvc.perform(get("/api/v1/rooms/1024/events").param("token", "query-token"))
            .andExpect(request().asyncStarted());
    }

    @Test
    void 토큰이_전혀_없으면_401() throws Exception {
        mockMvc.perform(get("/api/v1/rooms/1024/events"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void 형식이_잘못된_헤더는_유효한_쿼리_토큰이_있어도_401() throws Exception {
        given(tokenProvider.parse("query-token")).willReturn(1L);

        mockMvc.perform(get("/api/v1/rooms/1024/events")
                .header("Authorization", "Basic query-token")
                .param("token", "query-token"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void 토큰_검증에_실패하면_401() throws Exception {
        given(tokenProvider.parse(anyString())).willThrow(new UnauthorizedException("다시 접속해주세요"));

        mockMvc.perform(get("/api/v1/rooms/1024/events")
                .header("Authorization", "Bearer invalid-token"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void 없는_방이면_404() throws Exception {
        given(tokenProvider.parse(anyString())).willReturn(1L);
        willThrow(new RoomNotFoundException(999L)).given(subscribeRoomEventsService).validate(anyLong(), anyLong());

        mockMvc.perform(get("/api/v1/rooms/999/events")
                .header("Authorization", "Bearer valid-token"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("ROOM_NOT_FOUND"));
    }

    @Test
    void 만료된_방이면_410() throws Exception {
        given(tokenProvider.parse(anyString())).willReturn(1L);
        willThrow(new RoomExpiredException()).given(subscribeRoomEventsService).validate(anyLong(), anyLong());

        mockMvc.perform(get("/api/v1/rooms/1024/events")
                .header("Authorization", "Bearer valid-token"))
            .andExpect(status().isGone())
            .andExpect(jsonPath("$.code").value("ROOM_EXPIRED"));
    }

    @Test
    void 입장하지_않은_방이면_403() throws Exception {
        given(tokenProvider.parse(anyString())).willReturn(1L);
        willThrow(new RoomMembershipRequiredException()).given(subscribeRoomEventsService).validate(anyLong(), anyLong());

        mockMvc.perform(get("/api/v1/rooms/1024/events")
                .header("Authorization", "Bearer valid-token"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("ROOM_MEMBERSHIP_REQUIRED"));
    }
}
