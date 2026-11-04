package com.sssok.renewal.incoming.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
import javax.sql.DataSource;

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

    @Autowired
    DataSource dataSource;

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
    void 담당자를_지정하면_조회_결과에_보이고_null로_비울_수_있다() throws Exception {
        String token = 익명_인증("가현");
        long roomId = 방_만들고_입장(token);
        long reportId = 신고(token, roomId, 존재하는_미디어(roomId), "HARASSMENT");
        String adminToken = 슈퍼관리자_토큰();

        담당자_변경(adminToken, reportId, "\"HAENI\"")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.assignee").value("HAENI"));
        목록_조회(adminToken, null, null)
            .andExpect(jsonPath("$.data.reports[0].assignee").value("HAENI"));

        담당자_변경(adminToken, reportId, "null")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.assignee").doesNotExist());
    }

    @Test
    void 없는_담당자로_바꾸면_400이다() throws Exception {
        String token = 익명_인증("가현");
        long roomId = 방_만들고_입장(token);
        long reportId = 신고(token, roomId, 존재하는_미디어(roomId), "HARASSMENT");

        담당자_변경(슈퍼관리자_토큰(), reportId, "\"haeni\"").andExpect(status().isBadRequest());
    }

    @Test
    void 사진을_지우지_않고_처리_상태를_바꾸면_그_신고만_바뀌고_되돌릴_수도_있다() throws Exception {
        String hostToken = 익명_인증("가현");
        long roomId = 방_만들고_입장(hostToken);
        String guestToken = 익명_인증("민수");
        입장(guestToken, roomId);
        long mediaId = 존재하는_미디어(roomId);
        long reportId = 신고(hostToken, roomId, mediaId, "HARASSMENT");
        long sameMediaReportId = 신고(guestToken, roomId, mediaId, "PRIVACY_VIOLATION");
        String adminToken = 슈퍼관리자_토큰();

        상태_변경(adminToken, reportId, "REFLECTED")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("REFLECTED"))
            .andExpect(jsonPath("$.data.mediaDeleted").value(false));
        assertThat(상태(sameMediaReportId)).isEqualTo("UNREFLECTED");
        assertThat(jdbcTemplate.queryForObject(
            "SELECT count(*) FROM stored_file WHERE id = ?", Long.class, mediaId)).isEqualTo(1);

        상태_변경(adminToken, reportId, "UNREFLECTED")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("UNREFLECTED"));
    }

    @Test
    void 없는_처리_상태로_바꾸면_400이다() throws Exception {
        String token = 익명_인증("가현");
        long roomId = 방_만들고_입장(token);
        long reportId = 신고(token, roomId, 존재하는_미디어(roomId), "HARASSMENT");

        상태_변경(슈퍼관리자_토큰(), reportId, "DONE")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_MEDIA_REPORT_STATUS"));
        assertThat(상태(reportId)).isEqualTo("UNREFLECTED");
    }

    @Test
    void 사진을_삭제하면_갤러리에서_사라지고_같은_사진의_신고가_모두_처리완료된다() throws Exception {
        String hostToken = 익명_인증("가현");
        long roomId = 방_만들고_입장(hostToken);
        String guestToken = 익명_인증("민수");
        입장(guestToken, roomId);
        long mediaId = 존재하는_미디어(roomId);
        long otherMediaId = 존재하는_미디어(roomId);
        long reportId = 신고(hostToken, roomId, mediaId, "HARASSMENT");
        long sameMediaReportId = 신고(guestToken, roomId, mediaId, "PRIVACY_VIOLATION");
        long otherReportId = 신고(hostToken, roomId, otherMediaId, "HARASSMENT");

        사진_삭제(슈퍼관리자_토큰(), reportId)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("REFLECTED"))
            .andExpect(jsonPath("$.data.mediaDeleted").value(true))
            .andExpect(jsonPath("$.data.displayUrl").doesNotExist());

        assertThat(jdbcTemplate.queryForObject(
            "SELECT count(*) FROM stored_file WHERE id = ?", Long.class, mediaId)).isZero();
        assertThat(상태(reportId)).isEqualTo("REFLECTED");
        assertThat(상태(sameMediaReportId)).isEqualTo("REFLECTED");
        assertThat(상태(otherReportId)).isEqualTo("UNREFLECTED");
    }

    @Test
    void 이미_지워진_사진이어도_삭제_요청은_성공하고_처리완료로_바뀐다() throws Exception {
        String token = 익명_인증("가현");
        long roomId = 방_만들고_입장(token);
        long mediaId = 존재하는_미디어(roomId);
        long reportId = 신고(token, roomId, mediaId, "HARASSMENT");
        jdbcTemplate.update("DELETE FROM stored_file WHERE id = ?", mediaId);

        사진_삭제(슈퍼관리자_토큰(), reportId)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("REFLECTED"))
            .andExpect(jsonPath("$.data.mediaDeleted").value(true));
    }

    // 관리자 삭제는 stored_file 을 잠근 뒤 folder_media 를 지운다. 사용자 삭제가 folder_media 를 먼저
    // 지우면 서로의 잠금을 기다리는 교착이 된다. 관리자 쪽 잠금 순서를 그대로 재현해, 사용자 삭제가
    // stored_file 잠금에서 기다리며 folder_media 를 건드리지 않는지 확인한다.
    @Test
    void 관리자_삭제가_사진을_잠근_동안_사용자_삭제는_폴더_연결을_건드리지_않고_기다린다() throws Exception {
        String hostToken = 익명_인증("가현");
        long roomId = 방_만들고_입장(hostToken);
        long mediaId = 폴더에_담긴_미디어(roomId);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (Connection admin = dataSource.getConnection()) {
            admin.setAutoCommit(false);
            실행(admin, "SELECT id FROM stored_file WHERE id = ? FOR UPDATE", mediaId);

            Future<Integer> userDelete = executor.submit(() -> 사용자_삭제(hostToken, roomId, mediaId));
            잠금을_기다릴_때까지_대기(admin);

            // 사용자 삭제가 folder_media 를 먼저 지웠다면 여기서 사용자 트랜잭션을 기다리다 교착이 난다.
            실행(admin, "SET LOCAL lock_timeout = '3s'");
            실행(admin, "DELETE FROM folder_media WHERE media_id = ?", mediaId);
            실행(admin, "DELETE FROM stored_file WHERE id = ?", mediaId);
            admin.commit();

            assertThat(userDelete.get(10, TimeUnit.SECONDS)).isEqualTo(200);
        } finally {
            executor.shutdownNow();
        }
        assertThat(jdbcTemplate.queryForObject(
            "SELECT count(*) FROM stored_file WHERE id = ?", Long.class, mediaId)).isZero();
    }

    @Test
    void 폴더에_담긴_같은_사진을_사용자와_관리자가_동시에_지워도_둘_다_성공한다() throws Exception {
        String hostToken = 익명_인증("가현");
        long roomId = 방_만들고_입장(hostToken);
        String adminToken = 슈퍼관리자_토큰();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < 10; round++) {
                long mediaId = 폴더에_담긴_미디어(roomId);
                long reportId = 신고(hostToken, roomId, mediaId, "HARASSMENT");
                CountDownLatch start = new CountDownLatch(1);

                Future<Integer> userDelete = executor.submit(동시에(start,
                    () -> 사용자_삭제(hostToken, roomId, mediaId)));
                Future<Integer> adminDelete = executor.submit(동시에(start,
                    () -> 사진_삭제(adminToken, reportId).andReturn().getResponse().getStatus()));
                start.countDown();

                assertThat(userDelete.get(10, TimeUnit.SECONDS)).isEqualTo(200);
                assertThat(adminDelete.get(10, TimeUnit.SECONDS)).isEqualTo(200);
                assertThat(jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM stored_file WHERE id = ?", Long.class, mediaId)).isZero();
                assertThat(상태(reportId)).isEqualTo("REFLECTED");
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void 일반_관리자도_신고를_조회하고_처리할_수_있다() throws Exception {
        String token = 익명_인증("가현");
        long roomId = 방_만들고_입장(token);
        long reportId = 신고(token, roomId, 존재하는_미디어(roomId), "HARASSMENT");
        String adminToken = 일반관리자_토큰("reportadmin" + ADMIN_SEQUENCE.getAndIncrement());

        목록_조회(adminToken, null, null).andExpect(status().isOk());
        사진_삭제(adminToken, reportId).andExpect(status().isOk());
    }

    @Test
    void 관리자가_아닌_사용자_토큰으로는_신고_API를_쓸_수_없다() throws Exception {
        String token = 익명_인증("가현");
        long roomId = 방_만들고_입장(token);
        long mediaId = 존재하는_미디어(roomId);
        long reportId = 신고(token, roomId, mediaId, "HARASSMENT");

        목록_조회(token, null, null).andExpect(status().isUnauthorized());
        단건_조회(token, reportId).andExpect(status().isUnauthorized());
        담당자_변경(token, reportId, "\"HAENI\"").andExpect(status().isUnauthorized());
        상태_변경(token, reportId, "REFLECTED").andExpect(status().isUnauthorized());
        사진_삭제(token, reportId).andExpect(status().isUnauthorized());

        assertThat(jdbcTemplate.queryForObject(
            "SELECT count(*) FROM stored_file WHERE id = ?", Long.class, mediaId)).isEqualTo(1);
    }

    private int 사용자_삭제(String token, long roomId, long mediaId) throws Exception {
        return mockMvc.perform(delete("/api/v1/rooms/{roomId}/media/{mediaId}", roomId, mediaId)
                .header("Authorization", "Bearer " + token))
            .andReturn().getResponse().getStatus();
    }

    private long 폴더에_담긴_미디어(long roomId) {
        long mediaId = 존재하는_미디어(roomId);
        Long folderId = jdbcTemplate.queryForObject("""
            INSERT INTO folder (room_id, name, created_at, updated_at)
            VALUES (?, ?, now(), now()) RETURNING id
            """, Long.class, roomId, "f" + (mediaId % 100_000_000));
        jdbcTemplate.update("""
            INSERT INTO folder_media (folder_id, media_id, created_at, updated_at)
            VALUES (?, ?, now(), now())
            """, folderId, mediaId);
        return mediaId;
    }

    private static <T> Callable<T> 동시에(CountDownLatch start, Callable<T> task) {
        return () -> {
            start.await();
            return task.call();
        };
    }

    private void 실행(Connection connection, String sql, Object... params) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                statement.setObject(i + 1, params[i]);
            }
            statement.execute();
        }
    }

    // 다른 세션이 행 잠금을 기다리기 시작할 때까지 기다린다. 테스트 커넥션 풀이 작아 이미 쥔
    // 커넥션으로 확인한다. pg_stat_activity 는 트랜잭션 안에서 값이 고정되므로 pg_locks 를 본다.
    private void 잠금을_기다릴_때까지_대기(Connection connection) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (System.nanoTime() < deadline) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT count(*) FROM pg_locks WHERE NOT granted");
                 ResultSet result = statement.executeQuery()) {
                result.next();
                if (result.getLong(1) > 0) {
                    return;
                }
            }
            Thread.sleep(20);
        }
        throw new AssertionError("사용자 삭제가 잠금을 기다리지 않았다");
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

    private ResultActions 담당자_변경(String token, long reportId, String assigneeJson) throws Exception {
        return mockMvc.perform(patch("/api/v1/admin/media-reports/{reportId}/assignee", reportId)
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"assignee\":" + assigneeJson + "}"));
    }

    private ResultActions 상태_변경(String token, long reportId, String status) throws Exception {
        return mockMvc.perform(patch("/api/v1/admin/media-reports/{reportId}/status", reportId)
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"status\":\"" + status + "\"}"));
    }

    private ResultActions 사진_삭제(String token, long reportId) throws Exception {
        return mockMvc.perform(delete("/api/v1/admin/media-reports/{reportId}/media", reportId)
            .header("Authorization", "Bearer " + token));
    }

    private String 상태(long reportId) {
        return jdbcTemplate.queryForObject("SELECT status FROM media_report WHERE id = ?", String.class, reportId);
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
