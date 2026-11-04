package com.sssok.renewal.incoming.report;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sssok.infrastructure.persistence.admin.AdminJpaEntity;
import com.sssok.infrastructure.persistence.admin.AdminJpaRepository;
import com.sssok.renewal.outgoing.storage.FileStorage;
import com.sssok.support.AcceptanceTest;
import com.sssok.support.PostgresContainerSupport;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

// MediaReportApiTest 와 같은 설정이라야 테스트 컨텍스트를 재사용한다.
@AcceptanceTest
@SpringBootTest(properties = "storage.cleanup.auto-purge=false")
class AdminMediaReportApiTest extends PostgresContainerSupport {

    private static final String SUPER_ADMIN_LOGIN_ID = "superadmin";
    private static final String SUPER_ADMIN_PASSWORD = "testpass123";
    private static final AtomicInteger ADMIN_SEQUENCE = new AtomicInteger();
    private static final AtomicLong MEDIA_ID_SEQUENCE = new AtomicLong(System.currentTimeMillis() + 2_000_000);

    @Autowired
    WebApplicationContext context;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    AdminJpaRepository adminJpaRepository;

    @MockitoBean
    FileStorage fileStorage;

    MockMvc mockMvc;

    // 목록 조회는 테이블 전체를 읽으므로 빈 상태에서 시작한다.
    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        jdbcTemplate.update("DELETE FROM media_report");
        given(fileStorage.presignGet(any(), any(), any(), any())).willReturn("https://signed.example/photo");
    }

    @Test
    void 신고_목록은_최신순으로_신고_내용만_내려주고_새_신고는_미처리다() throws Exception {
        String token = 익명_인증("가현");
        long roomId = 방_만들고_입장(token);
        long first = 신고(token, roomId, 존재하는_미디어(roomId), "HARASSMENT");
        long second = 신고(token, roomId, 존재하는_미디어(roomId), "PRIVACY_VIOLATION");

        목록_조회(슈퍼관리자_토큰(), null, null)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.reports.length()").value(2))
            .andExpect(jsonPath("$.data.reports[0].reportId").value(second))
            .andExpect(jsonPath("$.data.reports[0].roomId").value(roomId))
            .andExpect(jsonPath("$.data.reports[0].roomName").value("우테코 회식"))
            .andExpect(jsonPath("$.data.reports[0].nickname").value("가현"))
            .andExpect(jsonPath("$.data.reports[0].reason").value("PRIVACY_VIOLATION"))
            .andExpect(jsonPath("$.data.reports[0].status").value("UNREFLECTED"))
            .andExpect(jsonPath("$.data.reports[0].assignee").doesNotExist())
            .andExpect(jsonPath("$.data.reports[0].reportedAt").isNotEmpty())
            .andExpect(jsonPath("$.data.reports[0].displayUrl").doesNotExist())
            .andExpect(jsonPath("$.data.reports[1].reportId").value(first))
            .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @Test
    void 신고_목록은_커서로_다음_페이지를_이어서_내려준다() throws Exception {
        String token = 익명_인증("가현");
        long roomId = 방_만들고_입장(token);
        long first = 신고(token, roomId, 존재하는_미디어(roomId), "HARASSMENT");
        long second = 신고(token, roomId, 존재하는_미디어(roomId), "HARASSMENT");
        long third = 신고(token, roomId, 존재하는_미디어(roomId), "HARASSMENT");
        String adminToken = 슈퍼관리자_토큰();

        목록_조회(adminToken, null, 2)
            .andExpect(jsonPath("$.data.reports[0].reportId").value(third))
            .andExpect(jsonPath("$.data.reports[1].reportId").value(second))
            .andExpect(jsonPath("$.data.nextCursor").value(second))
            .andExpect(jsonPath("$.data.hasNext").value(true));
        목록_조회(adminToken, second, 2)
            .andExpect(jsonPath("$.data.reports.length()").value(1))
            .andExpect(jsonPath("$.data.reports[0].reportId").value(first))
            .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @Test
    void 페이지_크기가_범위를_벗어나면_400이다() throws Exception {
        목록_조회(슈퍼관리자_토큰(), null, 101).andExpect(status().isBadRequest());
    }

    @Test
    void 신고_단건은_대상_사진_URL과_같은_사진의_신고_수를_함께_내려준다() throws Exception {
        String hostToken = 익명_인증("가현");
        long roomId = 방_만들고_입장(hostToken);
        String guestToken = 익명_인증("민수");
        입장(guestToken, roomId);
        long mediaId = 존재하는_미디어(roomId);
        long reportId = 신고(hostToken, roomId, mediaId, "COPYRIGHT_VIOLATION");
        신고(guestToken, roomId, mediaId, "HARASSMENT");

        단건_조회(슈퍼관리자_토큰(), reportId)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.reportId").value(reportId))
            .andExpect(jsonPath("$.data.roomId").value(roomId))
            .andExpect(jsonPath("$.data.mediaId").value(mediaId))
            .andExpect(jsonPath("$.data.nickname").value("가현"))
            .andExpect(jsonPath("$.data.roomName").value("우테코 회식"))
            .andExpect(jsonPath("$.data.reason").value("COPYRIGHT_VIOLATION"))
            .andExpect(jsonPath("$.data.mediaDeleted").value(false))
            .andExpect(jsonPath("$.data.displayUrl").value("https://signed.example/photo"))
            .andExpect(jsonPath("$.data.reportCountOfMedia").value(2));
    }

    @Test
    void 없는_신고를_조회하면_404다() throws Exception {
        단건_조회(슈퍼관리자_토큰(), Long.MAX_VALUE)
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("MEDIA_REPORT_NOT_FOUND"));
    }

    @Test
    void 일반_관리자도_신고를_조회할_수_있다() throws Exception {
        String token = 익명_인증("가현");
        long roomId = 방_만들고_입장(token);
        long reportId = 신고(token, roomId, 존재하는_미디어(roomId), "HARASSMENT");
        String adminToken = 일반관리자_토큰("reportadmin" + ADMIN_SEQUENCE.getAndIncrement());

        목록_조회(adminToken, null, null).andExpect(status().isOk());
        단건_조회(adminToken, reportId).andExpect(status().isOk());
    }

    @Test
    void 관리자가_아닌_사용자_토큰으로는_신고_API를_쓸_수_없다() throws Exception {
        String token = 익명_인증("가현");
        long roomId = 방_만들고_입장(token);
        long mediaId = 존재하는_미디어(roomId);
        long reportId = 신고(token, roomId, mediaId, "HARASSMENT");

        목록_조회(token, null, null).andExpect(status().isUnauthorized());
        단건_조회(token, reportId).andExpect(status().isUnauthorized());
    }

    private ResultActions 목록_조회(String token, Long cursor, Integer size) throws Exception {
        var request = get("/api/v1/admin/media-reports").header("Authorization", "Bearer " + token);
        if (cursor != null) {
            request = request.param("cursor", String.valueOf(cursor));
        }
        if (size != null) {
            request = request.param("size", String.valueOf(size));
        }
        return mockMvc.perform(request);
    }

    private ResultActions 단건_조회(String token, long reportId) throws Exception {
        return mockMvc.perform(get("/api/v1/admin/media-reports/{reportId}", reportId)
            .header("Authorization", "Bearer " + token));
    }

    private long 신고(String token, long roomId, long mediaId, String reason) throws Exception {
        mockMvc.perform(post("/api/v1/rooms/{roomId}/media/{mediaId}/reports", roomId, mediaId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"" + reason + "\"}"))
            .andExpect(status().isCreated());
        return jdbcTemplate.queryForObject(
            "SELECT max(id) FROM media_report WHERE media_id = ?", Long.class, mediaId);
    }

    private long 존재하는_미디어(long roomId) {
        long mediaId = MEDIA_ID_SEQUENCE.incrementAndGet();
        jdbcTemplate.update("""
            INSERT INTO stored_file
                (id, room_id, uploader_id, original_file_name, media_type, file_size_bytes,
                 storage_key, status, created_at, updated_at, reserved_at, retry_count)
            VALUES (?, ?, 1, 'test.jpg', 'JPEG', 1024, ?, 'READY', now(), now(), now(), 0)
            """, mediaId, roomId, "admin-report-test-key-" + mediaId);
        return mediaId;
    }

    private long 방_만들고_입장(String token) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/rooms")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"우테코 회식\"}"))
            .andReturn();
        return Long.parseLong(값(created, "roomId"));
    }

    private void 입장(String token, long roomId) throws Exception {
        String code = jdbcTemplate.queryForObject("SELECT code FROM room WHERE id = ?", String.class, roomId);
        mockMvc.perform(post("/api/v1/rooms/{roomId}/members", roomId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + code + "\"}"))
            .andExpect(status().is2xxSuccessful());
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

    private String 일반관리자_토큰(String loginId) throws Exception {
        mockMvc.perform(post("/api/v1/admin/accounts")
                .header("Authorization", "Bearer " + 슈퍼관리자_토큰())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"loginId\":\"" + loginId + "\",\"password\":\"password123\""
                    + ",\"name\":\"신고담당\",\"role\":\"ADMIN\"}"))
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
        MvcResult result = mockMvc.perform(post("/api/v1/auth/anonymous").header("X-CSRF-Protection", "1")
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
