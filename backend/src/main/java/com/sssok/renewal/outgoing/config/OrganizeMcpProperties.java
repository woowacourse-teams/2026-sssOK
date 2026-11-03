package com.sssok.renewal.outgoing.config;

import java.net.URI;
import java.time.Duration;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gallery.organization.mcp")
public record OrganizeMcpProperties(String serverId, URI url, String token, Set<String> exposedTools,
                                   Duration timeout) {
    public OrganizeMcpProperties {
        serverId = serverId == null ? "gallery" : serverId;
        timeout = timeout == null ? Duration.ofSeconds(10) : timeout;
        exposedTools = exposedTools == null ? Set.of() : Set.copyOf(exposedTools);
        if (serverId.isBlank() || url == null || token == null || token.isBlank()
            || exposedTools.stream().anyMatch(String::isBlank)
            || timeout.isZero() || timeout.isNegative() || timeout.compareTo(Duration.ofSeconds(30)) > 0) {
            throw new IllegalArgumentException("사진 정리 MCP 설정을 확인해주세요");
        }
        boolean localHttp = "http".equals(url.getScheme())
            && ("localhost".equals(url.getHost()) || "127.0.0.1".equals(url.getHost()));
        if (url.getHost() == null || url.getUserInfo() != null || url.getQuery() != null
            || url.getFragment() != null || (!"https".equals(url.getScheme()) && !localHttp)) {
            throw new IllegalArgumentException("MCP 주소는 HTTPS 또는 로컬 HTTP여야 합니다");
        }
    }

    @Override
    public String toString() {
        return "OrganizeMcpProperties[serverId=" + serverId + ", token=REDACTED]";
    }
}
