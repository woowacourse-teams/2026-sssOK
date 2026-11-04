package com.sssok.renewal.incoming.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sssok.infrastructure.realtime.InMemorySseEventPublisher;
import com.sssok.renewal.application.auth.AuthResult;
import com.sssok.renewal.application.organization.execute.OrganizeExecutionRequest;
import com.sssok.renewal.application.organization.interpret.OrganizeInterpretation;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

// API 인수 테스트. 컨트롤러 → 서비스 → 저장소 → 실제 PostgreSQL(Flyway·ddl-auto=validate)까지 전체가 맞물리는지,
// 대표 흐름만 확인한다. 분석 서버(MCP 도구·실행기)와 스토리지만 목이다.
// 규칙 하나하나(멱등·사용권·기한·재시도 상한·반영 충돌 등)는 계층별 테스트가 맡는다:
// 서비스+저장소는 renewal/application/organization(H2·PostgreSQL), 컨트롤러는 *ControllerTest, 저장소는 outgoing/database.
class OrganizeJobApiTest extends OrganizeJobApiSupport {
    @Autowired
    InMemorySseEventPublisher sseEventPublisher;

    // SSE 연결은 타임아웃이 없어 저절로 끝나지 않는다. 테스트가 연 연결은 끝나고 닫는다.
    @AfterEach
    void closeEmitters() {
        sseEventPublisher.completeAll(roomId);
    }

    private ResultActions apply(String token, String jobId, Object body) throws Exception {
        return mockMvc.perform(post(BASE + "/{jobId}/apply", roomId, jobId).header("Authorization", token)
            .header("Idempotency-Key", key()).contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(body)));
    }

    private ResultActions retry(String token, String jobId) throws Exception {
        return mockMvc.perform(post(BASE + "/{jobId}/retry", roomId, jobId).header("Authorization", token)
            .header("Idempotency-Key", key()));
    }

    private ResultActions cancel(String token, String jobId) throws Exception {
        return mockMvc.perform(post(BASE + "/{jobId}/cancel", roomId, jobId).header("Authorization", token));
    }

    private MvcResult subscribe(String token) throws Exception {
        return mockMvc.perform(get("/api/v1/rooms/{roomId}/events", roomId).header("Authorization", token))
            .andExpect(request().asyncStarted()).andReturn();
    }

    private String stream(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private Map<String, Object> group(String groupId, String folderName, List<Long> mediaIds) {
        return Map.of("groupId", groupId, "folderName", folderName, "mediaIds", mediaIds);
    }

    @Test
    void 접수에서_분석_검토_폴더_반영까지_한_번에_흘러간다() throws Exception {
        List<Long> ids = jpegs(4);
        MvcResult hostStream = subscribe(hostToken);
        MvcResult guestStream = subscribe(guestToken);

        // 접수(202) → 발송 → 분석 서버 접수(RUNNING)
        String jobId = startPerson();
        awaitStatus(jobId, "RUNNING");
        ArgumentCaptor<OrganizeExecutionRequest> sent = ArgumentCaptor.forClass(OrganizeExecutionRequest.class);
        verify(executor, atLeastOnce()).execute(sent.capture());
        assertThat(sent.getValue().jobId()).isEqualTo(UUID.fromString(jobId));
        assertThat(sent.getValue().photos()).extracting(photo -> photo.mediaId()).containsExactlyElementsOf(ids);

        // 방은 검토가 끝날 때까지 다른 접수를 막는다
        startByKind(guestToken, "SIMILAR").andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("ORGANIZE_JOB_IN_PROGRESS"))
            .andExpect(jsonPath("$.activeJobId").doesNotExist());

        // 진행 이벤트와 완료 이벤트(내부 API, 서비스 토큰)
        Map<String, Object> running = event(jobId, "RUNNING", "PERSON");
        running.put("progress", Map.of("stage", "detecting_faces", "counts", Map.of("processed", 1, "total", 4)));
        sendEvent("Bearer event-token", running).andExpect(status().isOk()).andExpect(jsonPath("$.accepted").value(true));
        complete(jobId, personResult(ids.subList(0, 3), List.of(ids.get(3)))).andExpect(status().isOk());
        getJob(hostToken, jobId).andExpect(jsonPath("$.data.status").value("READY"))
            .andExpect(jsonPath("$.data.reviewExpiresAt").isNotEmpty())
            .andExpect(jsonPath("$.data.resultExpiresAt").isNotEmpty());

        // 검토 결과 조회
        mockMvc.perform(get(BASE + "/{jobId}/result", roomId, jobId).header("Authorization", hostToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.groups[0].groupId").value("person_0001"))
            .andExpect(jsonPath("$.data.groups[0].photoCount").value(3))
            .andExpect(jsonPath("$.data.unassigned.photoCount").value(1));

        // 폴더 반영 → APPLIED, 방 해제, 폴더·연결 저장
        apply(hostToken, jobId, Map.of("groups", List.of(group("person_0001", "민찬", ids.subList(0, 2)))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("APPLIED"))
            .andExpect(jsonPath("$.data.folders[0].folderName").value("민찬"));
        assertThat(statusOf(jobId)).isEqualTo("APPLIED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM folder WHERE room_id = ? AND name = '민찬'", Long.class,
            roomId)).isEqualTo(1);
        mockMvc.perform(get(BASE + "/current", roomId).header("Authorization", guestToken))
            .andExpect(jsonPath("$.data.roomBusy").value(false));
        startByKind(guestToken, "SIMILAR").andExpect(status().isAccepted());

        // 실시간: 요청자는 작업 상세를 받고, 다른 멤버는 방 상태만 받는다(자기 작업의 상세는 자기 것만 받는다)
        assertThat(stream(hostStream)).contains("event:organize.job.updated").contains(jobId);
        assertThat(stream(guestStream)).contains("event:organize.room.changed").doesNotContain(jobId);
    }

    @Test
    void 문장으로_접수하면_모델이_고른_종류로_작업이_만들어진다() throws Exception {
        jpegs(2);
        given(interpreter.interpret("사람별로 모아줘")).willReturn(new OrganizeInterpretation.ToolSelected(PERSON_TOOL));

        mockMvc.perform(post(BASE + "/by-text", roomId).header("Authorization", hostToken)
                .header("Idempotency-Key", key()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"사람별로 모아줘\"}"))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.data.kind").value("PERSON"));
    }

    @Test
    void 진행_중인_작업을_취소하면_방이_풀리고_늦게_온_결과는_버린다() throws Exception {
        List<Long> ids = jpegs(2);
        String jobId = startPerson();
        awaitStatus(jobId, "RUNNING");

        cancel(hostToken, jobId).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("CANCELED"));
        complete(jobId, personResult(ids, List.of())).andExpect(status().isOk())
            .andExpect(jsonPath("$.accepted").value(true));

        assertThat(statusOf(jobId)).isEqualTo("CANCELED");
        startByKind(hostToken, "SIMILAR").andExpect(status().isAccepted());
        cancel(guestToken, jobId).andExpect(status().isNotFound());
    }

    @Test
    void 분석이_실패하면_같은_작업으로_다시_시도해_새_회차를_보낸다() throws Exception {
        jpegs(2);
        String jobId = startPerson();
        awaitStatus(jobId, "RUNNING");
        Map<String, Object> failed = event(jobId, "FAILED", "PERSON");
        failed.put("errorCode", "SERVER_RESTARTED");
        sendEvent("Bearer event-token", failed).andExpect(status().isOk());
        getJob(hostToken, jobId).andExpect(jsonPath("$.data.status").value("FAILED"))
            .andExpect(jsonPath("$.data.retryable").value(true));
        clock.advance(Duration.ofSeconds(6));

        retry(hostToken, jobId).andExpect(status().isAccepted())
            .andExpect(jsonPath("$.data.attempt").value(2));

        awaitStatus(jobId, "RUNNING");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM organize_execution e JOIN organize_job j ON j.id = e.job_id "
            + "WHERE j.public_id = ?", Long.class, UUID.fromString(jobId))).isEqualTo(2);
    }

    @Test
    void 검토_기한이_지나면_결과는_410이고_새_접수가_가능하다() throws Exception {
        List<Long> ids = jpegs(2);
        String jobId = startPerson();
        awaitStatus(jobId, "RUNNING");
        complete(jobId, personResult(ids, List.of())).andExpect(status().isOk());
        clock.advance(Duration.ofMinutes(31));

        mockMvc.perform(get(BASE + "/{jobId}/result", roomId, jobId).header("Authorization", hostToken))
            .andExpect(status().isGone()).andExpect(jsonPath("$.code").value("ORGANIZE_REVIEW_EXPIRED"));
        getJob(hostToken, jobId).andExpect(jsonPath("$.data.status").value("EXPIRED"));
        startByKind(guestToken, "SIMILAR").andExpect(status().isAccepted());
    }

    @Test
    void 같은_키는_작업을_다시_만들지_않고_다른_입력이면_409다() throws Exception {
        jpegs(2);
        String key = key();
        String first = startByKind(hostToken, "PERSON", key).andExpect(status().isAccepted())
            .andReturn().getResponse().getContentAsString();

        startByKind(hostToken, "PERSON", key).andExpect(status().isAccepted())
            .andExpect(jsonPath("$.data.jobId").value(objectMapper.readTree(first).path("data").path("jobId").asText()));
        startByKind(hostToken, "SIMILAR", key).andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_CONFLICT"));
        startByKind(hostToken, "PERSON", "short").andExpect(status().isBadRequest());
    }

    @Test
    void 익명_회원은_방마다_체험_한_번만_쓴다() throws Exception {
        AuthResult anonymous = anonymousAuthService.authenticate("서연");
        roomMembershipService.join(roomId, roomCode, anonymous.userId());
        String token = "Bearer " + anonymous.accessToken();
        jpegs(2);
        String jobId = objectMapper.readTree(startByKind(token, "PERSON").andExpect(status().isAccepted())
            .andReturn().getResponse().getContentAsString()).path("data").path("jobId").asText();
        awaitStatus(jobId, "RUNNING");
        cancel(token, jobId).andExpect(status().isOk());

        startByKind(token, "PERSON").andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("ORGANIZE_TRIAL_EXHAUSTED"));
    }

    @Test
    void 반영에서_이름이_충돌하면_409이고_실제_DB에서_모두_되돌아간다() throws Exception {
        List<Long> ids = jpegs(4);
        String jobId = startPerson();
        awaitStatus(jobId, "RUNNING");
        complete(jobId, personResult(ids.subList(0, 3), List.of(ids.get(3)))).andExpect(status().isOk());
        createFolderService.create(roomId, "민찬");

        apply(hostToken, jobId, Map.of("groups", List.of(group("person_0001", "민찬", ids.subList(0, 2)))))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("ORGANIZE_FOLDER_NAME_CONFLICT"));

        assertThat(statusOf(jobId)).isEqualTo("READY");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM folder_media WHERE folder_id IN "
            + "(SELECT id FROM folder WHERE room_id = ?)", Long.class, roomId)).isZero();
    }

    @Test
    void 분석할_사진이_없으면_422다() throws Exception {
        startByKind(hostToken, "PERSON").andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("ORGANIZE_NO_ANALYZABLE_PHOTOS"));
    }

    @Test
    void 내부_이벤트는_서비스_토큰이_있어야_받는다() throws Exception {
        jpegs(2);
        String jobId = startPerson();

        sendEvent("Bearer wrong", event(jobId, "RUNNING", "PERSON")).andExpect(status().isUnauthorized());
        sendEvent("Bearer event-token", event(jobId, "RUNNING", "PERSON")).andExpect(status().isOk());
    }

    @Test
    void 방_멤버가_아니거나_인증이_없으면_거절한다() throws Exception {
        AuthResult stranger = anonymousAuthService.authenticate("지수");

        mockMvc.perform(get(BASE + "/current", roomId).header("Authorization", "Bearer " + stranger.accessToken()))
            .andExpect(status().isForbidden());
        mockMvc.perform(get(BASE + "/current", roomId)).andExpect(status().isUnauthorized());
    }
}
