package com.sssok.presentation.api.feedback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

// API 인수 테스트 — 의견을 남긴 뒤 관리자 토큰으로 목록·단건 조회를 관통 확인한다.
// 커서 페이지네이션과 정렬은 AdminFeedbackPaginationApiTest 가 따로 본다.
@SpringBootTest(properties = {
    "spring.jpa.hibernate.ddl-auto=validate",
    "feedback.rate-limit-window=0s"
})
@AutoConfigureMockMvc
class AdminFeedbackApiTest extends PostgresContainerSupport {

    private static final Duration RETENTION = Duration.ofDays(7);

    // V19 마이그레이션이 심는 초기 슈퍼관리자.
    private static final String SUPER_ADMIN_LOGIN_ID = "superadmin";
    private static final String SUPER_ADMIN_PASSWORD = "testpass123";

    private static final String CHROME_UA =
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) "
            + "Chrome/140.0.0.0 Safari/537.36";
    private static final String FRONTEND_VERSION = "fe-v0.1.0";

    private static final AtomicInteger ADMIN_SEQUENCE = new AtomicInteger();

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    AdminJpaRepository adminJpaRepository;

    @Autowired
    FeedbackJpaRepository feedbackJpaRepository;

    @Autowired
    PurgeRoomService purgeRoomService;

    @Autowired
    RoomRepository roomRepository;

    @Autowired
    MemberRepository memberRepository;

    // 목록 조회는 테이블 전체를 읽는다. 다른 테스트가 남긴 의견이 섞이면 "몇 번째 항목"
    // 검증이 그 테스트의 실행 순서에 따라 흔들리므로, 각 테스트를 빈 상태에서 시작한다.
    @BeforeEach
    void 의견_데이터_초기화() {
        feedbackJpaRepository.deleteAll();
    }

    @Test
    void 관리자는_의견_목록을_최신순으로_조회한다() throws Exception {
        String memberToken = 익명_인증("가현");
        long roomId = 방_만들고_입장(memberToken);
        long older = 의견_남기기(memberToken, roomId, "먼저 남긴 의견");
        long newer = 의견_남기기(memberToken, roomId, "나중에 남긴 의견");

        목록_조회(슈퍼관리자_토큰(), 20)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.feedbacks", Matchers.hasSize(2)))
            .andExpect(jsonPath("$.data.feedbacks[0].feedbackId").value(newer))
            .andExpect(jsonPath("$.data.feedbacks[0].content").value("나중에 남긴 의견"))
            .andExpect(jsonPath("$.data.feedbacks[1].feedbackId").value(older));
    }

    @Test
    void 목록_항목에는_방_이름과_닉네임과_작성_시각이_함께_담긴다() throws Exception {
        String memberToken = 익명_인증("가현");
        long roomId = 방_만들고_입장(memberToken);
        long feedbackId = 의견_남기기(memberToken, roomId, "맥락이 같이 보여야 해요");

        목록_조회(슈퍼관리자_토큰(), 20)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.feedbacks[0].feedbackId").value(feedbackId))
            .andExpect(jsonPath("$.data.feedbacks[0].roomId").value(roomId))
            .andExpect(jsonPath("$.data.feedbacks[0].roomName").value("우테코 회식"))
            .andExpect(jsonPath("$.data.feedbacks[0].memberId").value(Matchers.notNullValue()))
            .andExpect(jsonPath("$.data.feedbacks[0].nickname").value("가현"))
            .andExpect(jsonPath("$.data.feedbacks[0].createdAt").value(Matchers.notNullValue()));
    }

    @Test
    void 목록의_본문은_잘리고_단건은_전문으로_내려온다() throws Exception {
        String memberToken = 익명_인증("가현");
        long roomId = 방_만들고_입장(memberToken);
        String longContent = "가".repeat(150);
        long feedbackId = 의견_남기기(memberToken, roomId, longContent);
        String adminToken = 슈퍼관리자_토큰();

        목록_조회(adminToken, 20)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.feedbacks[0].content").value("가".repeat(100) + "..."));

        단건_조회(adminToken, feedbackId)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content").value(longContent));
    }

    @Test
    void 관리자는_의견_단건의_전문과_맥락을_조회한다() throws Exception {
        String memberToken = 익명_인증("가현");
        long roomId = 방_만들고_입장(memberToken);
        long feedbackId = 의견_남기기(memberToken, roomId, "진행률이 멈춘 것처럼 보여요");

        단건_조회(슈퍼관리자_토큰(), feedbackId)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.feedbackId").value(feedbackId))
            .andExpect(jsonPath("$.data.content").value("진행률이 멈춘 것처럼 보여요"))
            .andExpect(jsonPath("$.data.roomId").value(roomId))
            .andExpect(jsonPath("$.data.roomName").value("우테코 회식"))
            .andExpect(jsonPath("$.data.memberId").value(Matchers.notNullValue()))
            .andExpect(jsonPath("$.data.nickname").value("가현"))
            .andExpect(jsonPath("$.data.createdAt").value(Matchers.notNullValue()));
    }

    @Test
    void 없는_의견을_조회하면_404() throws Exception {
        단건_조회(슈퍼관리자_토큰(), -1L)
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("FEEDBACK_NOT_FOUND"));
    }

    @Test
    void 의견_식별자가_숫자가_아니면_400() throws Exception {
        mockMvc.perform(get("/api/v1/admin/feedbacks/{feedbackId}", "없는것")
                .header("Authorization", "Bearer " + 슈퍼관리자_토큰()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST_PARAMETER"));
    }

    @Test
    void 저장된_userAgent와_frontendVersion이_그대로_내려온다() throws Exception {
        String memberToken = 익명_인증("가현");
        long roomId = 방_만들고_입장(memberToken);
        long feedbackId = 의견_남기기(memberToken, roomId, "아이폰에서 썸네일이 늦게 떠요");
        String adminToken = 슈퍼관리자_토큰();

        목록_조회(adminToken, 20)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.feedbacks[0].userAgent").value(CHROME_UA))
            .andExpect(jsonPath("$.data.feedbacks[0].frontendVersion").value(FRONTEND_VERSION));

        단건_조회(adminToken, feedbackId)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.userAgent").value(CHROME_UA))
            .andExpect(jsonPath("$.data.frontendVersion").value(FRONTEND_VERSION));
    }

    @Test
    void 헤더_없이_저장된_의견도_조회되고_userAgent와_frontendVersion이_null이다() throws Exception {
        String memberToken = 익명_인증("가현");
        long roomId = 방_만들고_입장(memberToken);
        long feedbackId = 의견_남기기(memberToken, roomId, "curl 로 보냈어요", null, null);
        String adminToken = 슈퍼관리자_토큰();

        목록_조회(adminToken, 20)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.feedbacks[0].feedbackId").value(feedbackId))
            .andExpect(jsonPath("$.data.feedbacks[0].userAgent").value(Matchers.nullValue()))
            .andExpect(jsonPath("$.data.feedbacks[0].frontendVersion").value(Matchers.nullValue()));

        단건_조회(adminToken, feedbackId)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.userAgent").value(Matchers.nullValue()))
            .andExpect(jsonPath("$.data.frontendVersion").value(Matchers.nullValue()));
    }

    @Test
    void 방을_purge_해도_의견이_조회되고_맥락_값이_보존된다() throws Exception {
        String memberToken = 익명_인증("가현");
        long roomId = 방_만들고_입장(memberToken);
        long feedbackId = 의견_남기기(memberToken, roomId, "방이 사라져도 이 의견은 읽혀야 해요");
        Long memberId = feedbackJpaRepository.findById(feedbackId).orElseThrow().getMemberId();

        방_삭제(memberToken, roomId).andExpect(status().isOk());
        보존_기간이_지난_삭제로_되돌리기(roomId);
        purgeRoomService.purgeAll(Instant.now());

        assertThat(roomRepository.findById(roomId)).isEmpty();
        assertThat(memberRepository.findById(memberId)).isEmpty();

        String adminToken = 슈퍼관리자_토큰();
        단건_조회(adminToken, feedbackId)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content").value("방이 사라져도 이 의견은 읽혀야 해요"))
            .andExpect(jsonPath("$.data.roomId").value(roomId))
            .andExpect(jsonPath("$.data.roomName").value("우테코 회식"))
            .andExpect(jsonPath("$.data.memberId").value(memberId))
            .andExpect(jsonPath("$.data.nickname").value("가현"));

        목록_조회(adminToken, 20)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.feedbacks[0].feedbackId").value(feedbackId))
            .andExpect(jsonPath("$.data.feedbacks[0].roomName").value("우테코 회식"))
            .andExpect(jsonPath("$.data.feedbacks[0].nickname").value("가현"));
    }

    @Test
    void 일반_관리자도_의견을_조회한다() throws Exception {
        String memberToken = 익명_인증("가현");
        long roomId = 방_만들고_입장(memberToken);
        long feedbackId = 의견_남기기(memberToken, roomId, "일반 관리자도 읽어야 해요");
        String adminToken = 일반관리자_토큰();

        목록_조회(adminToken, 20).andExpect(status().isOk());
        단건_조회(adminToken, feedbackId).andExpect(status().isOk());
    }

    @Test
    void 익명_사용자_토큰으로_조회하면_거부된다() throws Exception {
        String memberToken = 익명_인증("가현");
        long roomId = 방_만들고_입장(memberToken);
        long feedbackId = 의견_남기기(memberToken, roomId, "남의 의견을 보려는 시도");

        // 관리자 토큰이 아니라 토큰 단계에서 걸려 401 이 난다.
        목록_조회(memberToken, 20).andExpect(status().isUnauthorized());
        단건_조회(memberToken, feedbackId).andExpect(status().isUnauthorized());
    }

    @Test
    void 토큰_없이_조회하면_401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/feedbacks")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/admin/feedbacks/{feedbackId}", 1L))
            .andExpect(status().isUnauthorized());
    }

    private ResultActions 목록_조회(String token, Integer size) throws Exception {
        var request = get("/api/v1/admin/feedbacks").header("Authorization", "Bearer " + token);
        if (size != null) {
            request = request.param("size", String.valueOf(size));
        }
        return mockMvc.perform(request);
    }

    private ResultActions 단건_조회(String token, long feedbackId) throws Exception {
        return mockMvc.perform(get("/api/v1/admin/feedbacks/{feedbackId}", feedbackId)
            .header("Authorization", "Bearer " + token));
    }

    private long 의견_남기기(String token, long roomId, String content) throws Exception {
        return 의견_남기기(token, roomId, content, CHROME_UA, FRONTEND_VERSION);
    }

    private long 의견_남기기(
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

    private long 방_만들고_입장(String token) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/rooms")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"우테코 회식\"}"))
            .andReturn();
        long roomId = Long.parseLong(값(created, "roomId"));

        mockMvc.perform(post("/api/v1/rooms/{roomId}/members", roomId)
            .header("Authorization", "Bearer " + token));

        return roomId;
    }

    private ResultActions 방_삭제(String token, long roomId) throws Exception {
        return mockMvc.perform(delete("/api/v1/rooms/{roomId}", roomId)
            .header("Authorization", "Bearer " + token));
    }

    private void 보존_기간이_지난_삭제로_되돌리기(long roomId) {
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
    private String 슈퍼관리자_토큰() throws Exception {
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
    private String 일반관리자_토큰() throws Exception {
        String loginId = "feedbackreader" + ADMIN_SEQUENCE.getAndIncrement();
        mockMvc.perform(post("/api/v1/admin/accounts")
                .header("Authorization", "Bearer " + 슈퍼관리자_토큰())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"loginId\":\"" + loginId + "\",\"password\":\"password123\""
                    + ",\"name\":\"의견담당\",\"role\":\"ADMIN\"}"))
            .andExpect(status().isCreated());
        return 로그인_토큰(loginId, "password123");
    }

    private String 로그인_토큰(String loginId, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/admin/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"loginId\":\"" + loginId + "\",\"password\":\"" + password + "\"}"))
            .andExpect(status().isOk())
            .andReturn();
        return 값(result, "accessToken");
    }

    private String 익명_인증(String nickname) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/anonymous")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\":\"" + nickname + "\"}"))
            .andReturn();
        return 값(result, "accessToken");
    }

    private String 값(MvcResult result, String field) throws Exception {
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("data").get(field).asText();
    }
}
