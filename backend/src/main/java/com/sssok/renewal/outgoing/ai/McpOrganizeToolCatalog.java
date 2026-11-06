package com.sssok.renewal.outgoing.ai;

import com.sssok.renewal.application.organization.interpret.InterpretationException;
import com.sssok.renewal.application.organization.interpret.InterpretationException.Reason;
import com.sssok.renewal.application.organization.interpret.OrganizeTool;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema.ListToolsResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

public class McpOrganizeToolCatalog {
    private static final int MAX_PAGES = 100;

    private final String serverId;
    private final Set<String> exposedTools;
    private final Supplier<McpSyncClient> clientFactory;

    public McpOrganizeToolCatalog(String serverId, Set<String> exposedTools, Supplier<McpSyncClient> clientFactory) {
        this.serverId = serverId;
        this.exposedTools = Set.copyOf(exposedTools);
        this.clientFactory = clientFactory;
    }

    public List<OrganizeTool> fetchTools() {
        try (McpSyncClient client = clientFactory.get()) {
            client.initialize();
            return listExposedTools(client);
        } catch (RuntimeException failure) {
            throw new InterpretationException(Reason.CATALOG_UNAVAILABLE);
        }
    }

    List<OrganizeTool> listExposedTools(McpSyncClient client) {
        List<OrganizeTool> exposed = new ArrayList<>();
        Set<String> seenNames = new HashSet<>();
        Set<String> seenCursors = new HashSet<>();
        String cursor = null;
        do {
            ListToolsResult page = client.listTools(cursor);
            for (Tool tool : page.tools()) {
                requireUniqueName(seenNames, tool);
                if (isExposed(tool)) {
                    exposed.add(new OrganizeTool(serverId, tool.name(), tool.description()));
                }
            }
            cursor = page.nextCursor();
            requireNewCursor(seenCursors, cursor);
        } while (cursor != null);
        return List.copyOf(exposed);
    }

    private void requireUniqueName(Set<String> seenNames, Tool tool) {
        if (!seenNames.add(tool.name())) {
            throw new IllegalArgumentException("MCP 도구 이름이 중복되었습니다");
        }
    }

    private void requireNewCursor(Set<String> seenCursors, String cursor) {
        if (cursor == null) {
            return;
        }
        boolean repeated = !seenCursors.add(cursor);
        if (repeated || seenCursors.size() >= MAX_PAGES) {
            throw new IllegalArgumentException("MCP 도구 목록 페이지를 확인할 수 없습니다");
        }
    }

    private boolean isExposed(Tool tool) {
        return exposedTools.contains(tool.name()) && OrganizeToolInputContract.accepts(tool.inputSchema());
    }
}
