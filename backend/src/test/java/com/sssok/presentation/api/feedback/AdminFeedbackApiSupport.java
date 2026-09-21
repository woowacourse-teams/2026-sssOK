package com.sssok.presentation.api.feedback;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sssok.application.port.out.MemberRepository;
import com.sssok.application.port.out.RoomRepository;
import com.sssok.application.room.PurgeRoomService;
import com.sssok.domain.room.Room;
import com.sssok.infrastructure.persistence.admin.AdminJpaEntity;
import com.sssok.infrastructure.persistence.admin.AdminJpaRepository;
import com.sssok.infrastructure.persistence.feedback.FeedbackJpaRepository;
import com.sssok.support.PostgresContainerSupport;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

// 관리자 의견 조회 인수 테스트가 함께 쓰는 준비 과정. 방을 만들고 입장해 의견을 남기고,
// 관리자 토큰을 얻는 절차가 테스트마다 반복되는데 검증과 섞이면 읽기 어려워진다.
@SpringBootTest(properties = {
    "spring.jpa.hibernate.ddl-auto=validate",
    "feedback.rate-limit-window=0s"
})
@AutoConfigureMockMvc
abstract class AdminFeedbackApiSupport extends PostgresContainerSupport {

    protected static final Duration RETENTION = Duration.ofDays(7);

    // V19 마이그레이션이 심는 초기 슈퍼관리자.
    protected static final String SUPER_ADMIN_LOGIN_ID = "superadmin";
    protected static final String SUPER_ADMIN_PASSWORD = "testpass123";

    protected static final String CHROME_UA =
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) "
            + "Chrome/140.0.0.0 Safari/537.36";
    protected static final String FRONTEND_VERSION = "fe-v0.1.0";

    private static final AtomicInteger ADMIN_SEQUENCE = new AtomicInteger();

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected AdminJpaRepository adminJpaRepository;

    @Autowired
    protected FeedbackJpaRepository feedbackJpaRepository;

    @Autowired
    protected PurgeRoomService purgeRoomService;

    @Autowired
    protected RoomRepository roomRepository;

    @Autowired
    protected MemberRepository memberRepository;

    // 목록 조회는 테이블 전체를 읽는다. 다른 테스트가 남긴 의견이 섞이면 "몇 번째 항목"
    // 검증이 그 테스트의 실행 순서에 따라 흔들리므로, 각 테스트를 빈 상태에서 시작한다.
    @BeforeEach
    void 의견_데이터_초기화() {
        feedbackJpaRepository.deleteAll();
    }

    protected ResultActions 목록_조회(String token, Long cursor, Integer size) throws Exception {
        var request = get("/api/v1/admin/feedbacks").header("Authorization", "Bearer " + token);
        if (cursor != null) {
            request = request.param("cursor", String.valueOf(cursor));
        }
        if (size != null) {
            request = request.param("size", String.valueOf(size));
        }
        return mockMvc.perform(request);
    }

    protected ResultActions 단건_조회(String token, long feedbackId) throws Exception {
        return mockMvc.perform(get("/api/v1/admin/feedbacks/{feedbackId}", feedbackId)
            .header("Authorization", "Bearer " + token));
    }

    protected long 의견_남기기(String token, long roomId, String content) throws Exception {
        return 의견_남기기(token, roomId, content, CHROME_UA, FRONTEND_VERSION);
    }

    protected long 의견_남기기(
        String token, long roomId, String content, String userAgent, String frontendVersion
    ) throws Exception {
        var request = post("/api/v1/rooms/{roomId}/feedbacks", roomId)
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"content\":\"" + content + "\"}");
        if (userAgent != null) {
            request = request.header(HttpHeaders.USER_AGENT, userAgent);
        }
        if (frontendVersion != null) {
            request = request.header("X-App-Version", frontendVersion);
        }
        MvcResult created = mockMvc.perform(request).andExpect(status().isCreated()).andReturn();
        return Long.parseLong(값(created, "feedbackId"));
    }

    protected long 방_만들고_입장(String token) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/rooms")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"우테코 회식\"}"))
            .andReturn();
        long roomId = Long.parseLong(값(created, "roomId"));
        방_입장(token, roomId);
        return roomId;
    }

    protected void 방_입장(String token, long roomId) throws Exception {
        mockMvc.perform(post("/api/v1/rooms/{roomId}/members", roomId)
            .header("Authorization", "Bearer " + token));
    }

    protected ResultActions 방_삭제(String token, long roomId) throws Exception {
        return mockMvc.perform(delete("/api/v1/rooms/{roomId}", roomId)
            .header("Authorization", "Bearer " + token));
    }

    protected void 보존_기간이_지난_삭제로_되돌리기(long roomId) {
        Room stored = roomRepository.findById(roomId).orElseThrow();
        roomRepository.save(Room.reconstruct(
            stored.getId(),
            stored.getVersion(),
            stored.getCode(),
            stored.getName(),
            stored.getStatus(),
            stored.getExpiration(),
            stored.getUploadPolicy(),
            stored.getHostId(),
            stored.getCreatedAt(),
            Instant.now().minus(RETENTION).minus(Duration.ofDays(1))
        ));
    }

    // 초기 슈퍼관리자의 비밀번호는 테스트가 모르므로 테스트용으로 바꿔 두고 로그인한다.
    protected String 슈퍼관리자_토큰() throws Exception {
        AdminJpaEntity stored = adminJpaRepository.findByLoginId(SUPER_ADMIN_LOGIN_ID).orElseThrow();
        adminJpaRepository.save(new AdminJpaEntity(
            stored.getId(),
            stored.getLoginId(),
            new BCryptPasswordEncoder().encode(SUPER_ADMIN_PASSWORD),
            stored.getName(),
            stored.getRole(),
            stored.getCreatedAt()
        ));
        return 로그인_토큰(SUPER_ADMIN_LOGIN_ID, SUPER_ADMIN_PASSWORD);
    }

    // 계정 아이디는 전체 관리자 중 유일해야 해서, 호출마다 다른 아이디를 쓴다.
    protected String 일반관리자_토큰() throws Exception {
        return 일반관리자_토큰("feedbackreader" + ADMIN_SEQUENCE.getAndIncrement());
    }

    protected String 일반관리자_토큰(String loginId) throws Exception {
        mockMvc.perform(post("/api/v1/admin/accounts")
                .header("Authorization", "Bearer " + 슈퍼관리자_토큰())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"loginId\":\"" + loginId + "\",\"password\":\"password123\""
                    + ",\"name\":\"의견담당\",\"role\":\"ADMIN\"}"))
            .andExpect(status().isCreated());
        return 로그인_토큰(loginId, "password123");
    }

    protected String 로그인_토큰(String loginId, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/admin/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"loginId\":\"" + loginId + "\",\"password\":\"" + password + "\"}"))
            .andExpect(status().isOk())
            .andReturn();
        return 값(result, "accessToken");
    }

    protected String 익명_인증(String nickname) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/anonymous")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\":\"" + nickname + "\"}"))
            .andReturn();
        return 값(result, "accessToken");
    }

    protected String 값(MvcResult result, String field) throws Exception {
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("data").get(field).asText();
    }
}
