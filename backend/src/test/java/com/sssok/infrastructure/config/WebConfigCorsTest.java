package com.sssok.infrastructure.config;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sssok.application.port.out.RoomMemberRepository;
import com.sssok.application.port.out.RoomRepository;
import com.sssok.application.port.out.TokenProvider;
import com.sssok.application.room.CreateRoomService;
import com.sssok.application.room.DeleteRoomService;
import com.sssok.application.room.GetRoomService;
import com.sssok.application.room.JoinRoomService;
import com.sssok.application.room.RoomDetail;
import com.sssok.application.room.UpdateRoomService;
import com.sssok.domain.room.Room;
import com.sssok.domain.room.RoomCode;
import com.sssok.domain.room.RoomExpiration;
import com.sssok.domain.room.RoomName;
import com.sssok.domain.room.UploadPolicy;
import com.sssok.domain.room.roomstatus.RoomStatus;
import com.sssok.presentation.api.room.RoomController;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// WebConfig가 등록하는 CORS 설정을 검증한다. 특정 컨트롤러의 기능이 아니라 WebConfig 자체를
// 확인하는 것이므로, 인증이 필요 없는 기존 엔드포인트(RoomController)에 붙여 테스트한다.
@WebMvcTest(RoomController.class)
class WebConfigCorsTest {

    private static final String CODE = "A3F9K2M7";
    // application-local.yml/application-test.yml에 설정된 기본 허용 오리진과 맞춘다.
    private static final String ALLOWED_ORIGIN = "http://localhost:3000";

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    GetRoomService getRoomService;

    // RoomController 생성자가 요구하는 나머지 서비스. 이 테스트에서는 쓰지 않지만
    // 컨텍스트가 뜨려면 빈으로 채워져야 한다.
    @MockitoBean
    CreateRoomService createRoomService;

    @MockitoBean
    UpdateRoomService updateRoomService;

    @MockitoBean
    DeleteRoomService deleteRoomService;

    @MockitoBean
    JoinRoomService joinRoomService;

    @MockitoBean
    TokenProvider tokenProvider;

    @MockitoBean
    RoomRepository roomRepository;

    @MockitoBean
    RoomMemberRepository roomMemberRepository;

    @Test
    void 허용된_오리진의_프리플라이트_요청은_허용_헤더를_받는다() throws Exception {
        mockMvc.perform(options("/api/v1/rooms/{code}", CODE)
                .header("Origin", ALLOWED_ORIGIN)
                .header("Access-Control-Request-Method", "GET"))
            .andExpect(status().isOk())
            .andExpect(header().string("Access-Control-Allow-Origin", ALLOWED_ORIGIN))
            .andExpect(header().exists("Access-Control-Allow-Methods"));
    }

    @Test
    void 허용되지_않은_오리진의_프리플라이트_요청은_거부된다() throws Exception {
        mockMvc.perform(options("/api/v1/rooms/{code}", CODE)
                .header("Origin", "https://evil.example.com")
                .header("Access-Control-Request-Method", "GET"))
            .andExpect(status().isForbidden());
    }

    @Test
    void 허용된_오리진의_실제_요청_응답에도_허용_헤더가_붙는다() throws Exception {
        given(getRoomService.getByCode(eq(new RoomCode(CODE)), isNull())).willReturn(roomDetail());

        mockMvc.perform(get("/api/v1/rooms/{code}", CODE)
                .header("Origin", ALLOWED_ORIGIN))
            .andExpect(status().isOk())
            .andExpect(header().string("Access-Control-Allow-Origin", ALLOWED_ORIGIN));
    }

    private RoomDetail roomDetail() {
        Room room = Room.reconstruct(
            10L, null, new RoomCode(CODE), new RoomName("우테코 회식"), RoomStatus.initial(),
            new RoomExpiration(Instant.parse("2026-08-14T00:00:00Z")), UploadPolicy.ANYONE, 1L,
            Instant.parse("2026-08-13T00:00:00Z"), null);
        return new RoomDetail(room, "가현", false, 0, List.of());
    }
}
