package com.sssok.renewal.outgoing.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

class McpAuthenticationCheckTest {
    @ParameterizedTest
    @CsvSource({"401, true", "403, true", "406, false", "500, false"})
    void 인증_거절_응답만_토큰_거절로_판단한다(int status, boolean rejected) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/mcp", exchange -> {
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
        });
        server.start();
        try {
            var check = new McpAuthenticationCheck(
                URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/mcp"), "token", Duration.ofSeconds(2));
            assertThat(check.rejectsToken()).isEqualTo(rejected);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void 서버에_연결할_수_없으면_토큰_거절로_판단하지_않는다() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        int closedPort = server.getAddress().getPort();
        server.stop(0);

        var check = new McpAuthenticationCheck(URI.create("http://127.0.0.1:" + closedPort + "/mcp"), "token",
            Duration.ofSeconds(2));
        assertThat(check.rejectsToken()).isFalse();
    }
}
