package com.sssok.renewal.outgoing.ai;

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
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class McpOrganizeToolExecutor {
    private final McpOrganizeToolCatalog catalog;
    private final Supplier<McpSyncClient> clientFactory;
    private final McpAuthenticationCheck authenticationCheck;

    public McpOrganizeToolExecutor(McpOrganizeToolCatalog catalog, Supplier<McpSyncClient> clientFactory,
                                   McpAuthenticationCheck authenticationCheck) {
        this.catalog = catalog;
        this.clientFactory = clientFactory;
        this.authenticationCheck = authenticationCheck;
    }

    public OrganizeExecutionReceipt execute(OrganizeExecutionRequest request) {
        CallToolResult result = callSelectedTool(request);
        return toReceipt(result, request);
    }

    private CallToolResult callSelectedTool(OrganizeExecutionRequest request) {
        try (McpSyncClient client = clientFactory.get()) {
            requireSelectedToolUnchanged(client, request.tool());
            return callTool(client, request);
        }
    }

    private void requireSelectedToolUnchanged(McpSyncClient client, OrganizeTool selected) {
        if (!listCurrentTools(client).contains(selected)) {
            throw new OrganizeExecutionException(Reason.TOOL_CHANGED);
        }
    }

    private List<OrganizeTool> listCurrentTools(McpSyncClient client) {
        try {
            client.initialize();
            return catalog.listExposedTools(client);
        } catch (RuntimeException failure) {
            throw new OrganizeExecutionException(
                authenticationCheck.rejectsToken() ? Reason.AUTHENTICATION_FAILED : Reason.NOT_DELIVERED);
        }
    }

    private CallToolResult callTool(McpSyncClient client, OrganizeExecutionRequest request) {
        try {
            return client.callTool(new CallToolRequest(request.tool().name(), toArguments(request)));
        } catch (McpError rejected) {
            throw new OrganizeExecutionException(Reason.REJECTED);
        } catch (RuntimeException failure) {
            throw new OrganizeExecutionException(Reason.OUTCOME_UNKNOWN);
        }
    }

    private Map<String, Object> toArguments(OrganizeExecutionRequest request) {
        return Map.of(
            "room_id", request.roomId(),
            "job_id", request.jobId().toString(),
            "request_id", request.executionId().toString(),
            "attempt", request.attempt(),
            "photos", request.photos().stream().map(this::toPhotoArgument).toList());
    }

    private Map<String, Object> toPhotoArgument(AnalysisPhoto photo) {
        return Map.of("mediaId", photo.mediaId(), "source", photo.source(), "extension", photo.extension());
    }

    private OrganizeExecutionReceipt toReceipt(CallToolResult result, OrganizeExecutionRequest request) {
        if (result == null) {
            throw new OrganizeExecutionException(Reason.INVALID_RESPONSE);
        }
        if (Boolean.TRUE.equals(result.isError())) {
            throw new OrganizeExecutionException(isRetryable(result) ? Reason.SERVER_BUSY : Reason.REJECTED);
        }
        if (!(result.structuredContent() instanceof Map<?, ?> receipt) || !echoesRequest(receipt, request)) {
            throw new OrganizeExecutionException(Reason.INVALID_RESPONSE);
        }
        return new OrganizeExecutionReceipt(request.executionId(), parseState(receipt.get("state")),
            Boolean.TRUE.equals(receipt.get("reused")));
    }

    // 서버는 실행을 만들기 전에 일시적으로 거절할 때만 structuredContent 의 retryable 을 true 로 준다.
    private boolean isRetryable(CallToolResult result) {
        return result.structuredContent() instanceof Map<?, ?> error && Boolean.TRUE.equals(error.get("retryable"));
    }

    private boolean echoesRequest(Map<?, ?> receipt, OrganizeExecutionRequest request) {
        return request.jobId().toString().equals(receipt.get("jobId"))
            && request.executionId().toString().equals(receipt.get("executionId"))
            && isNumberEqualTo(receipt.get("roomId"), request.roomId())
            && isNumberEqualTo(receipt.get("attempt"), request.attempt())
            && receipt.get("reused") instanceof Boolean;
    }

    private boolean isNumberEqualTo(Object value, long expected) {
        return (value instanceof Integer || value instanceof Long) && ((Number) value).longValue() == expected;
    }

    private State parseState(Object value) {
        return Arrays.stream(State.values())
            .filter(state -> state.name().equals(value))
            .findFirst()
            .orElseThrow(() -> new OrganizeExecutionException(Reason.INVALID_RESPONSE));
    }
}
