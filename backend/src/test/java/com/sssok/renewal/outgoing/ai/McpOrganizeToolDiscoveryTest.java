package com.sssok.renewal.outgoing.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sssok.renewal.application.organization.interpret.OrganizeTool;
import com.sssok.renewal.outgoing.config.OrganizeInterpretationConfig;
import com.sssok.renewal.outgoing.config.OrganizeMcpProperties;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;

class McpOrganizeToolDiscoveryTest {
    @Test
    void 실제_MCP_초기화와_인증된_tools_list로_목록을_읽는다() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        List<String> methods = new CopyOnWriteArrayList<>();
        List<String> authorization = new CopyOnWriteArrayList<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/mcp", exchange -> {
            try {
                if (!exchange.getRequestMethod().equals("POST")) {
                    exchange.sendResponseHeaders(405, -1);
                    return;
                }
                authorization.add(exchange.getRequestHeaders().getFirst("Authorization"));
                JsonNode request = mapper.readTree(exchange.getRequestBody());
                String method = request.path("method").asText();
                methods.add(method);
                if (!request.has("id")) {
                    exchange.sendResponseHeaders(202, -1);
                    return;
                }
                Object result = switch (method) {
                    case "initialize" -> Map.of("protocolVersion", request.at("/params/protocolVersion").asText(),
                        "capabilities", Map.of("tools", Map.of()), "serverInfo", Map.of("name", "test", "version", "1"));
                    case "tools/list" -> Map.of("tools", List.of(Map.of(
                        "name", "brand_new_analysis", "description", "새 분류 기능",
                        "inputSchema", Map.of("type", "object", "properties", Map.of(
                            "room_id", Map.of("type", "integer"), "job_id", Map.of("type", "string"),
                            "request_id", Map.of("type", "string"), "attempt", Map.of("type", "integer"),
                            "photos", Map.of("type", "array", "items", Map.of("type", "object"))),
                            "required", List.of("room_id", "job_id", "request_id", "attempt", "photos")))));
                    default -> Map.of();
                };
                byte[] response = mapper.writeValueAsBytes(Map.of("jsonrpc", "2.0", "id", request.get("id"), "result", result));
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.length);
                exchange.getResponseBody().write(response);
            } finally {
                exchange.close();
            }
        });
        server.start();
        try {
            var properties = new OrganizeMcpProperties("gallery",
                URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/mcp"), "test-token",
                Set.of("brand_new_analysis"), Duration.ofSeconds(3));
            var catalog = new OrganizeInterpretationConfig().organizeToolCatalog(properties);
            assertThat(catalog.fetchTools()).containsExactly(new OrganizeTool("gallery", "brand_new_analysis", "새 분류 기능"));
            assertThat(methods).contains("initialize", "tools/list").doesNotContain("tools/call");
            assertThat(authorization).isNotEmpty().allMatch("Bearer test-token"::equals);
        } finally {
            server.stop(0);
        }
    }
}
