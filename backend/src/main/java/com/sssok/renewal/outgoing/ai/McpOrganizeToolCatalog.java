package com.sssok.renewal.outgoing.ai;

import com.sssok.renewal.application.organization.interpret.InterpretationException;
import com.sssok.renewal.application.organization.interpret.InterpretationException.Reason;
import com.sssok.renewal.application.organization.interpret.OrganizeTool;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.modelcontextprotocol.spec.McpSchema.ListToolsResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

public class McpOrganizeToolCatalog {
    private static final int MAX_PAGES = 100;
    private static final Map<String, String> SERVER_INPUTS = Map.of(
        "room_id", "integer", "job_id", "string", "request_id", "string",
        "attempt", "integer", "photos", "array");
    private final String serverId;
    private final Set<String> exposedTools;
    private final Supplier<McpSyncClient> clientFactory;

    public McpOrganizeToolCatalog(String serverId, Set<String> exposedTools, Supplier<McpSyncClient> clientFactory) {
        this.serverId = serverId;
        this.exposedTools = Set.copyOf(exposedTools);
        this.clientFactory = clientFactory;
    }

    public List<OrganizeTool> fetchTools() {
        // 매 요청마다 목록을 새로 읽는다. 목록 변경 알림과 캐시는 필요할 때 도입한다.
        try (McpSyncClient client = clientFactory.get()) {
            client.initialize();
            return collectTools(client);
        } catch (RuntimeException failure) {
            throw new InterpretationException(Reason.CATALOG_UNAVAILABLE);
        }
    }

    private List<OrganizeTool> collectTools(McpSyncClient client) {
        List<OrganizeTool> available = new ArrayList<>();
        Set<String> names = new HashSet<>();
        Set<String> cursors = new HashSet<>();
        String cursor = null;
        do {
            ListToolsResult page = client.listTools(cursor);
            for (Tool tool : page.tools()) {
                if (!names.add(tool.name())) {
                    throw new IllegalArgumentException("MCP 도구 이름이 중복되었습니다");
                }
                if (exposedTools.contains(tool.name()) && supportsInput(tool.inputSchema())) {
                    available.add(new OrganizeTool(serverId, tool.name(), tool.description()));
                }
            }
            cursor = page.nextCursor();
            if (cursor != null && (!cursors.add(cursor) || cursors.size() >= MAX_PAGES)) {
                throw new IllegalArgumentException("MCP 도구 목록 페이지를 확인할 수 없습니다");
            }
        } while (cursor != null);
        return List.copyOf(available);
    }

    private boolean supportsInput(JsonSchema schema) {
        if (schema == null || !"object".equals(schema.type())) {
            return false;
        }
        if (!hasExpectedFields(schema) || !hasRequiredFields(schema)) {
            return false;
        }
        for (Map.Entry<String, String> input : SERVER_INPUTS.entrySet()) {
            Object definition = schema.properties().get(input.getKey());
            if (!(definition instanceof Map<?, ?> property)) {
                return false;
            }
            if (!input.getValue().equals(property.get("type"))) {
                return false;
            }
        }
        return true;
    }

    private boolean hasExpectedFields(JsonSchema schema) {
        return schema.properties() != null && schema.properties().keySet().equals(SERVER_INPUTS.keySet());
    }

    private boolean hasRequiredFields(JsonSchema schema) {
        return schema.required() != null && Set.copyOf(schema.required()).equals(SERVER_INPUTS.keySet());
    }
}
