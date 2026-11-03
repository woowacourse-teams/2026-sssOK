package com.sssok.renewal.outgoing.ai;

import static com.sssok.renewal.outgoing.ai.OrganizeToolSchemas.serverManagedInputs;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sssok.renewal.application.organization.execute.AnalysisPhoto;
import com.sssok.renewal.application.organization.execute.OrganizeExecutionException;
import com.sssok.renewal.application.organization.execute.OrganizeExecutionException.Reason;
import com.sssok.renewal.application.organization.execute.OrganizeExecutionReceipt;
import com.sssok.renewal.application.organization.execute.OrganizeExecutionReceipt.State;
import com.sssok.renewal.application.organization.execute.OrganizeExecutionRequest;
import com.sssok.renewal.application.organization.interpret.OrganizeTool;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.ListToolsResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

class McpOrganizeToolExecutorTest {
    private static final UUID JOB_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID EXECUTION_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final OrganizeTool TOOL = new OrganizeTool("gallery", "group_photos_by_person", "사람별로 묶는다");

    private final McpSyncClient client = mock(McpSyncClient.class);
    private final McpAuthenticationCheck authenticationCheck = mock(McpAuthenticationCheck.class);
    private final McpOrganizeToolCatalog catalog =
        new McpOrganizeToolCatalog("gallery", Set.of(TOOL.name()), () -> client);
    private final McpOrganizeToolExecutor executor =
        new McpOrganizeToolExecutor(catalog, () -> client, authenticationCheck);

    @BeforeEach
    void listCurrentTool() {
        when(client.listTools(null)).thenReturn(new ListToolsResult(List.of(tool(TOOL.description())), null));
    }

    @Test
    void 작업_관리가_정한_값으로_도구를_호출하고_접수_결과를_반환한다() {
        when(client.callTool(any(CallToolRequest.class))).thenReturn(accepted(receipt()));

        assertThat(executor.execute(request()))
            .isEqualTo(new OrganizeExecutionReceipt(EXECUTION_ID, State.QUEUED, false));

        ArgumentCaptor<CallToolRequest> sent = ArgumentCaptor.forClass(CallToolRequest.class);
        verify(client).callTool(sent.capture());
        assertThat(sent.getValue().name()).isEqualTo("group_photos_by_person");
        assertThat(sent.getValue().arguments()).containsOnlyKeys("room_id", "job_id", "request_id", "attempt", "photos")
            .containsEntry("room_id", 7L)
            .containsEntry("job_id", JOB_ID.toString())
            .containsEntry("request_id", EXECUTION_ID.toString())
            .containsEntry("attempt", 1)
            .containsEntry("photos", List.of(Map.of("mediaId", 42L, "source", "https://r2.test/signed", "extension", "jpg")));
        verify(client).close();
    }

    @Test
    void 같은_실행의_재전송은_기존_실행으로_받는다() {
        Map<String, Object> reused = receipt();
        reused.put("state", "COMPLETED");
        reused.put("reused", true);
        when(client.callTool(any(CallToolRequest.class))).thenReturn(accepted(reused));

        assertThat(executor.execute(request()))
            .isEqualTo(new OrganizeExecutionReceipt(EXECUTION_ID, State.COMPLETED, true));
    }

    @Test
    void 해석_이후_도구_설명이_바뀌면_호출하지_않는다() {
        when(client.listTools(null)).thenReturn(new ListToolsResult(List.of(tool("다른 설명")), null));

        assertReason(Reason.TOOL_CHANGED);
        verify(client, never()).callTool(any());
    }

    @Test
    void 호출_전_연결_실패는_접수되지_않은_것으로_구분한다() {
        when(client.initialize()).thenThrow(new IllegalStateException("connection refused"));

        assertReason(Reason.NOT_DELIVERED);
        verify(client, never()).callTool(any());
    }

    @Test
    void 토큰이_거절되면_인증_실패로_구분한다() {
        when(client.initialize()).thenThrow(new RuntimeException("Failed to send message"));
        when(authenticationCheck.rejectsToken()).thenReturn(true);

        assertReason(Reason.AUTHENTICATION_FAILED);
        verify(client, never()).callTool(any());
    }

    @Test
    void 호출_중_실패는_접수_여부를_모르는_것으로_구분한다() {
        when(client.callTool(any(CallToolRequest.class))).thenThrow(new IllegalStateException("timeout"));

        assertReason(Reason.OUTCOME_UNKNOWN);
        verify(client).close();
    }

    @Test
    void 서버가_거절하면_원문을_노출하지_않고_접수_거절로_구분한다() {
        when(client.callTool(any(CallToolRequest.class))).thenReturn(CallToolResult.builder()
            .content(List.of(new TextContent("Analysis queue is full; https://r2.test/signed"))).isError(true).build());

        assertThatThrownBy(() -> executor.execute(request()))
            .isInstanceOfSatisfying(OrganizeExecutionException.class,
                failure -> assertThat(failure.reason()).isEqualTo(Reason.REJECTED))
            .hasMessageNotContaining("r2.test");
    }

    @Test
    void 프로토콜_오류_응답은_재전송_대상이_아닌_접수_거절로_구분한다() {
        when(client.callTool(any(CallToolRequest.class)))
            .thenThrow(McpError.builder(-32602).message("Invalid arguments").build());

        assertReason(Reason.REJECTED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"jobId", "executionId", "roomId", "attempt", "state", "reused"})
    void 요청과_맞지_않는_응답은_거절한다(String field) {
        Map<String, Object> mismatched = receipt();
        mismatched.put(field, "unexpected");
        when(client.callTool(any(CallToolRequest.class))).thenReturn(accepted(mismatched));

        assertReason(Reason.INVALID_RESPONSE);
    }

    @Test
    void 구조화된_응답이_없으면_거절한다() {
        when(client.callTool(any(CallToolRequest.class))).thenReturn(CallToolResult.builder()
            .content(List.of(new TextContent("{}"))).isError(false).build());

        assertReason(Reason.INVALID_RESPONSE);
    }

    private void assertReason(Reason reason) {
        assertThatThrownBy(() -> executor.execute(request()))
            .isInstanceOfSatisfying(OrganizeExecutionException.class,
                failure -> assertThat(failure.reason()).isEqualTo(reason));
    }

    private OrganizeExecutionRequest request() {
        return new OrganizeExecutionRequest(TOOL, 7L, JOB_ID, EXECUTION_ID, 1,
            List.of(new AnalysisPhoto(42L, "https://r2.test/signed", "jpg")));
    }

    private Map<String, Object> receipt() {
        Map<String, Object> receipt = new HashMap<>();
        receipt.put("jobType", "PERSON");
        receipt.put("jobId", JOB_ID.toString());
        receipt.put("executionId", EXECUTION_ID.toString());
        receipt.put("attempt", 1);
        receipt.put("roomId", 7);
        receipt.put("state", "QUEUED");
        receipt.put("reused", false);
        return receipt;
    }

    private CallToolResult accepted(Map<String, Object> content) {
        return CallToolResult.builder().structuredContent(content).isError(false).build();
    }

    private Tool tool(String description) {
        return Tool.builder().name(TOOL.name()).description(description).inputSchema(serverManagedInputs()).build();
    }
}
