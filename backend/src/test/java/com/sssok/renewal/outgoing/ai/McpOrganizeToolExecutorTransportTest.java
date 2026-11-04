package com.sssok.renewal.outgoing.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sssok.renewal.application.organization.execute.AnalysisPhoto;
import com.sssok.renewal.application.organization.execute.OrganizeExecutionException;
import com.sssok.renewal.application.organization.execute.OrganizeExecutionException.Reason;
import com.sssok.renewal.application.organization.execute.OrganizeExecutionRequest;
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
import java.util.UUID;
import org.junit.jupiter.api.Test;

class McpOrganizeToolExecutorTransportTest {
    private static final OrganizeTool TOOL = new OrganizeTool("gallery", "brand_new_analysis", "새 분류 기능");

    @Test
    void 서버가_구조화된_일시적_거절을_주면_실제_SDK도_같은_실행으로_재전송할_거절로_읽는다() throws Exception {
        Map<String, Object> failure = Map.of("isError", true,
            "content", List.of(Map.of("type", "text", "text", "Analysis queue is full; retry later")),
            "structuredContent", Map.of("errorCode", "QUEUE_FULL", "retryable", true));

        assertThat(rejectionReasonFor(failure)).isEqualTo(Reason.SERVER_BUSY);
    }

    @Test
    void 구조화된_표시가_없는_오류_결과는_실제_SDK에서도_접수_거절이다() throws Exception {
        Map<String, Object> failure = Map.of("isError", true,
            "content", List.of(Map.of("type", "text", "text", "Error executing tool brand_new_analysis: invalid")));

        assertThat(rejectionReasonFor(failure)).isEqualTo(Reason.REJECTED);
    }

    private Reason rejectionReasonFor(Map<String, Object> toolCallResult) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/mcp", exchange -> {
            try {
                if (!exchange.getRequestMethod().equals("POST")) {
                    exchange.sendResponseHeaders(405, -1);
                    return;
                }
                JsonNode request = mapper.readTree(exchange.getRequestBody());
                if (!request.has("id")) {
                    exchange.sendResponseHeaders(202, -1);
                    return;
                }
                Object result = switch (request.path("method").asText()) {
                    case "initialize" -> Map.of("protocolVersion", request.at("/params/protocolVersion").asText(),
                        "capabilities", Map.of("tools", Map.of()), "serverInfo", Map.of("name", "test", "version", "1"));
                    case "tools/list" -> Map.of("tools", List.of(Map.of(
                        "name", TOOL.name(), "description", TOOL.description(),
                        "inputSchema", Map.of("type", "object", "properties", Map.of(
                            "room_id", Map.of("type", "integer"), "job_id", Map.of("type", "string"),
                            "request_id", Map.of("type", "string"), "attempt", Map.of("type", "integer"),
                            "photos", Map.of("type", "array", "items", Map.of("type", "object"))),
                            "required", List.of("room_id", "job_id", "request_id", "attempt", "photos")))));
                    case "tools/call" -> toolCallResult;
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
                Set.of(TOOL.name()), Duration.ofSeconds(3));
            var config = new OrganizeInterpretationConfig();
            var executor = config.organizeToolExecutor(config.organizeToolCatalog(properties), properties);
            var request = new OrganizeExecutionRequest(TOOL, 7L, UUID.randomUUID(), UUID.randomUUID(), 1,
                List.of(new AnalysisPhoto(42L, "https://r2.test/signed", "jpg")));
            try {
                executor.execute(request);
            } catch (OrganizeExecutionException failure) {
                return failure.reason();
            }
            throw new AssertionError("서버가 거절했으므로 예외가 나야 한다");
        } finally {
            server.stop(0);
        }
    }
}
