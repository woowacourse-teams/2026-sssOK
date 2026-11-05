package com.sssok.renewal.incoming.organization;

import static com.sssok.support.UploadSizePolicyFixture.SIZE_POLICY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sssok.application.folder.CreateFolderService;
import com.sssok.application.port.out.FileRepository;
import com.sssok.application.room.CreateRoomService;
import com.sssok.application.room.RoomMembershipService;
import com.sssok.domain.file.FileSize;
import com.sssok.domain.file.StoredFile;
import com.sssok.domain.room.Room;
import com.sssok.domain.room.RoomCode;
import com.sssok.renewal.application.auth.AnonymousAuthService;
import com.sssok.renewal.application.auth.AuthResult;
import com.sssok.renewal.application.organization.MutableClock;
import com.sssok.renewal.application.organization.OrganizeTestClockConfig;
import com.sssok.renewal.application.organization.interpret.InterpretOrganizeRequestService;
import com.sssok.renewal.application.organization.interpret.OrganizeTool;
import com.sssok.renewal.application.organization.job.OrganizeLivenessService;
import com.sssok.renewal.outgoing.ai.McpOrganizeToolCatalog;
import com.sssok.renewal.outgoing.ai.McpOrganizeToolExecutor;
import com.sssok.renewal.outgoing.storage.FileStorage;
import com.sssok.support.AcceptanceTest;
import com.sssok.support.PostgresContainerSupport;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

// #659 사진 정리 작업 API 테스트의 공통 준비. 분석 서버(MCP 도구·실행기)와 스토리지만 목으로 두고 DB는 실제로 쓴다.
// 이 클래스는 구현이 아니라 도구 상자이고, 시나리오는 하위 클래스에 있다.
@AcceptanceTest
@AutoConfigureMockMvc
@Import(OrganizeTestClockConfig.class)
@SpringBootTest(properties = {
    "spring.jpa.hibernate.ddl-auto=validate",
    "jwt.secret=bG9jYWwtY29udHJhY3QtdGVzdC1rZXktbm90LWZvci1wcm9kdWN0aW9uLTU4MQ==",
    "gallery.organization.job.enabled=true",
    "gallery.organization.job.internal-token=event-token",
    "gallery.organization.job.max-photos=5",
    "gallery.organization.job.start-interval=0s",
    "gallery.organization.job.dispatch.sweep-interval=1h",
    "gallery.organization.mcp.token=mcp-token"
})
abstract class OrganizeJobApiSupport extends PostgresContainerSupport {
    protected static final String BASE = "/api/v1/rooms/{roomId}/organize-jobs";
    protected static final OrganizeTool PERSON_TOOL =
        new OrganizeTool("gallery", "group_photos_by_person", "사람별로 사진을 묶는다");
    protected static final OrganizeTool SIMILAR_TOOL =
        new OrganizeTool("gallery", "group_similar_photos", "비슷한 사진끼리 묶는다");

    @Autowired
    @Qualifier("applicationTaskExecutor")
    AsyncTaskExecutor taskExecutor;
    @Autowired
    MutableClock clock;
    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    AnonymousAuthService anonymousAuthService;
    @Autowired
    CreateRoomService createRoomService;
    @Autowired
    RoomMembershipService roomMembershipService;
    @Autowired
    FileRepository fileRepository;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    OrganizeLivenessService livenessService;
    @Autowired
    CreateFolderService createFolderService;

    @MockitoBean
    FileStorage fileStorage;
    @MockitoBean
    McpOrganizeToolCatalog catalog;
    @MockitoBean
    McpOrganizeToolExecutor executor;
    @MockitoBean
    InterpretOrganizeRequestService interpreter;

    protected Long roomId;
    protected Long hostId;
    protected Long guestId;
    protected RoomCode roomCode;
    protected String hostToken;
    protected String guestToken;

    @BeforeEach
    void setUp() {
        clock.reset();
        given(fileStorage.presignGet(any(), any(), any(), any()))
            .willAnswer(invocation -> "https://storage.test/" + invocation.getArgument(0, com.sssok.domain.file.StorageKey.class).value());
        given(catalog.fetchTools()).willReturn(List.of(PERSON_TOOL, SIMILAR_TOOL));

        AuthResult host = anonymousAuthService.authenticate("가현");
        hostId = host.userId();
        hostToken = "Bearer " + host.accessToken();
        Room room = createRoomService.create(hostId, "정리 방", null).room();
        roomId = room.getId();

        AuthResult guest = anonymousAuthService.authenticate("민수");
        guestToken = "Bearer " + guest.accessToken();
        guestId = guest.userId();
        roomCode = room.getCode();
        roomMembershipService.join(roomId, roomCode, guest.userId());
        // 방장과 참여자는 OAuth 회원으로 둔다. 익명 회원의 방별 체험 1회는 따로 검증한다.
        register(hostId);
        register(guestId);
    }

    // Mockito가 목을 초기화하기 전에 이전 요청의 비동기 발송을 모두 끝낸다.
    @AfterEach
    void awaitDispatchCompletion() {
        ThreadPoolTaskExecutor pool = (ThreadPoolTaskExecutor) taskExecutor;
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(pool.getThreadPoolExecutor().getQueue()).isEmpty();
            assertThat(pool.getActiveCount()).isZero();
        });
    }

    protected void register(Long memberId) {
        jdbc.update("UPDATE member SET status = 'REGISTERED' WHERE id = ?", memberId);
    }

    protected String key() {
        return UUID.randomUUID().toString();
    }

    // ---- 도우미 ----

    protected Long photo(String fileName, String mimeType, boolean ready) {
        StoredFile file = StoredFile.reserve(roomId, hostId, fileName, mimeType, new FileSize(1024), Instant.now(),
            SIZE_POLICY);
        file.startProcessing();
        if (ready) {
            file.markReady();
        }
        return fileRepository.save(file).getId();
    }

    protected List<Long> jpegs(int count) {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            ids.add(photo("p" + i + ".jpg", "image/jpeg", true));
        }
        return ids;
    }

    protected ResultActions startByKind(String token, String kind) throws Exception {
        return startByKind(token, kind, key());
    }

    protected ResultActions startByKind(String token, String kind, String idempotencyKey) throws Exception {
        return mockMvc.perform(post(BASE, roomId).header("Authorization", token)
            .header("Idempotency-Key", idempotencyKey)
            .contentType(MediaType.APPLICATION_JSON).content("{\"kind\":\"" + kind + "\"}"));
    }

    protected String startPerson() throws Exception {
        String body = startByKind(hostToken, "PERSON").andExpect(status().isAccepted())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("jobId").asText();
    }

    protected ResultActions getJob(String token, String jobId) throws Exception {
        return mockMvc.perform(get(BASE + "/{jobId}", roomId, jobId).header("Authorization", token));
    }

    protected String statusOf(String jobId) {
        return jdbc.queryForObject("SELECT status FROM organize_job WHERE public_id = ?", String.class,
            UUID.fromString(jobId));
    }

    protected void awaitStatus(String jobId, String expected) {
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(statusOf(jobId)).isEqualTo(expected));
    }

    protected UUID executionIdOf(String jobId) {
        return jdbc.queryForObject("""
            SELECT e.execution_id FROM organize_execution e JOIN organize_job j ON j.id = e.job_id
            WHERE j.public_id = ?
            """, UUID.class, UUID.fromString(jobId));
    }

    protected ResultActions sendEvent(String token, Map<String, Object> body) throws Exception {
        return mockMvc.perform(post("/internal/gallery-analysis/events")
            .header("Authorization", token)
            .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)));
    }

    protected Map<String, Object> event(String jobId, String state, String jobType) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("jobType", jobType);
        body.put("jobId", jobId);
        body.put("executionId", executionIdOf(jobId).toString());
        body.put("attempt", 1);
        body.put("roomId", roomId);
        body.put("state", state);
        body.put("progress", Map.of());
        body.put("result", null);
        body.put("errorCode", null);
        return body;
    }

    protected Map<String, Object> personResult(List<Long> grouped, List<Long> unassigned) {
        return Map.of("groups", List.of(Map.of("groupId", "person_0001", "mediaIds", grouped,
                "faceMembershipCount", grouped.size(), "bodyAdditionCount", 0)),
            "unassignedMediaIds", unassigned, "errors", List.of(), "counts", Map.of(), "pipelineVersion", "v1");
    }

    protected ResultActions complete(String jobId, Map<String, Object> result) throws Exception {
        Map<String, Object> body = event(jobId, "COMPLETED", "PERSON");
        body.put("result", result);
        return sendEvent("Bearer event-token", body);
    }
}
