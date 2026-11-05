package com.sssok.renewal.application.organization;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sssok.domain.file.StorageKey;
import com.sssok.renewal.application.organization.interpret.InterpretOrganizeRequestService;
import com.sssok.renewal.application.organization.interpret.OrganizeTool;
import com.sssok.renewal.application.organization.job.OrganizeEvent;
import com.sssok.renewal.application.organization.job.OrganizeStartService;
import com.sssok.renewal.domain.organization.OrganizeJob;
import com.sssok.renewal.incoming.organization.OrganizeJobDispatchListener;
import com.sssok.renewal.outgoing.ai.McpOrganizeToolCatalog;
import com.sssok.renewal.outgoing.ai.McpOrganizeToolExecutor;
import com.sssok.renewal.outgoing.database.OrganizeJobRepository;
import com.sssok.renewal.outgoing.storage.FileStorage;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

// 사진 정리 서비스·저장소 통합 테스트의 공통 도구. 테스트마다 새 회원·방·사진을 만들어 서로 격리되므로 롤백이나 정리가 필요 없다.
// 목은 모두 여기서만 선언한다. 하위 클래스가 목을 추가하면 Spring 컨텍스트가 따로 만들어져 느려진다.
public abstract class OrganizeServiceTestSupport {
    protected static final OrganizeTool PERSON_TOOL =
        new OrganizeTool("gallery", "group_photos_by_person", "사람별로 사진을 묶는다");
    protected static final OrganizeTool SIMILAR_TOOL =
        new OrganizeTool("gallery", "group_similar_photos", "비슷한 사진끼리 묶는다");

    @Autowired
    protected JdbcTemplate jdbc;
    @Autowired
    protected OrganizeFixtures fixtures;
    @Autowired
    protected OrganizeJobRepository repository;
    @Autowired
    protected OrganizeStartService startService;
    @Autowired
    protected ObjectMapper objectMapper;
    @Autowired
    protected MutableClock clock;
    @Autowired
    protected OrganizeJobStateHelper states;

    @MockitoBean
    protected FileStorage fileStorage;
    @MockitoBean
    protected McpOrganizeToolCatalog catalog;
    @MockitoBean
    protected McpOrganizeToolExecutor executor;
    @MockitoBean
    protected InterpretOrganizeRequestService interpreter;
    // 커밋 뒤 자동 발송은 끈다. 발송은 테스트가 OrganizeDispatchService 를 직접 불러 결정적으로 확인한다.
    @MockitoBean
    protected OrganizeJobDispatchListener dispatchListener;

    @BeforeEach
    void setUpOrganizeSupport() {
        clock.reset();
        given(fileStorage.presignGet(any(), any(), any(), any()))
            .willAnswer(invocation -> "https://storage.test/" + invocation.getArgument(0, StorageKey.class).value());
        given(catalog.fetchTools()).willReturn(List.of(PERSON_TOOL, SIMILAR_TOOL));
    }

    // ---- 회원·방·사진 (OrganizeFixtures 에 위임) ----

    protected Long anonymousMember() {
        return fixtures.anonymousMember();
    }

    protected Long registeredMember() {
        return fixtures.registeredMember();
    }

    protected Long room(Long hostId) {
        return fixtures.room(hostId);
    }

    protected Long photo(Long roomId, Long uploaderId, String fileName, String mimeType, boolean ready) {
        return fixtures.photo(roomId, uploaderId, fileName, mimeType, ready);
    }

    protected List<Long> jpegs(Long roomId, Long uploaderId, int count) {
        return fixtures.jpegs(roomId, uploaderId, count);
    }

    // ---- 작업 시작과 분석 서버 이벤트 ----

    protected String key() {
        return fixtures.key();
    }

    protected OrganizeJob startPerson(Long roomId, Long memberId) {
        return fixtures.startPerson(roomId, memberId);
    }

    protected OrganizeJob reload(OrganizeJob job) {
        return repository.findById(job.id()).orElseThrow();
    }

    protected OrganizeEvent event(OrganizeJob job, String state, JsonNode result, String errorCode) {
        UUID executionId = repository.findLatestExecution(job.id()).orElseThrow().executionId();
        int attempt = repository.findLatestExecution(job.id()).orElseThrow().attempt();
        return new OrganizeEvent(job.publicId(), job.roomId(), executionId, attempt, job.kind().name(), state,
            objectMapper.createObjectNode(), result, errorCode);
    }

    protected OrganizeEvent running(OrganizeJob job) {
        return event(job, "RUNNING", null, null);
    }

    protected OrganizeEvent failed(OrganizeJob job, String errorCode) {
        return event(job, "FAILED", null, errorCode);
    }

    protected OrganizeEvent completed(OrganizeJob job, JsonNode result) {
        return event(job, "COMPLETED", result, null);
    }

    protected JsonNode personResult(List<Long> grouped, List<Long> unassigned) {
        return fixtures.personResult(grouped, unassigned);
    }
}
